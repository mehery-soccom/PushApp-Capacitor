package com.mehery.pushapp

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in API telemetry to a Slack Incoming Webhook (integration debugging only).
 * Enabled only when a valid [slackWebhookUrl] is passed to PushApp.initialize().
 * Request/response bodies and headers are redacted before posting.
 */
object SlackApiLogger {
    private const val TAG = "SlackApiLogger"
    private const val MIN_INTERVAL_MS = 2500L
    private const val MAX_PER_MINUTE = 12
    private const val MAX_FIELD_CHARS = 2000
    private const val MAX_SLACK_POST_RETRIES = 3
    private val webhookPattern =
        Regex("^https://hooks\\.slack\\.com/services/[A-Za-z0-9]+/[A-Za-z0-9]+/[A-Za-z0-9_-]+\$")

    @Volatile
    private var webhookUrl: String = ""

    @Volatile
    private var disabled = false

    private val slackClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val executor = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()
    private var lastSentAt = 0L
    private val sentTimestamps = ArrayDeque<Long>()
    private val pendingMessages = ArrayDeque<PendingMessage>()
    private val drainScheduled = AtomicBoolean(false)

    private data class PendingMessage(val text: String, var retries: Int = 0)

    fun configure(webhookUrl: String?) {
        disabled = false
        val trimmed = webhookUrl?.trim().orEmpty()
        this.webhookUrl = resolveWebhookUrl(trimmed).orEmpty()
        when {
            this.webhookUrl.isNotEmpty() -> {
                PushAppLogger.debug(TAG, "Slack API logging enabled (opt-in)")
                postConfiguredPing()
            }
            trimmed.isNotEmpty() ->
                PushAppLogger.error(
                    TAG,
                    "Invalid slackWebhookUrl. Use a real Incoming Webhook URL from " +
                        "Slack → Apps → Incoming Webhooks. API logs will not be sent.",
                )
            else ->
                PushAppLogger.debug(TAG, "Slack API logging disabled (no webhook URL provided)")
        }
    }

    private fun postConfiguredPing() {
        executor.execute {
            synchronized(lock) {
                pendingMessages.addFirst(
                    PendingMessage("[PushApp SDK] Slack API logging connected — API calls will appear here."),
                )
            }
            scheduleDrain()
        }
    }

    private fun resolveWebhookUrl(raw: String): String? {
        if (raw.isBlank()) return null
        if (raw.contains("YOUR/WEBHOOK", ignoreCase = true)) return null
        if (!webhookPattern.matches(raw)) return null
        return raw
    }

    fun logApiCall(
        platform: String,
        method: String,
        url: String,
        requestHeaders: Map<String, String>,
        requestBody: String?,
        statusCode: Int?,
        responseHeaders: Map<String, String>?,
        responseBody: String?,
        error: String?,
    ) {
        if (webhookUrl.isBlank() || disabled) {
            return
        }
        PushAppLogger.debug(TAG, "Queue Slack API log: $method $url")
        executor.execute {
            val message = buildMessage(
                platform = platform,
                method = method,
                url = url,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                statusCode = statusCode,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                error = error,
            )
            synchronized(lock) {
                pendingMessages.addLast(PendingMessage(message))
            }
            scheduleDrain()
        }
    }

    private fun scheduleDrain() {
        synchronized(lock) {
            if (drainScheduled.getAndSet(true)) return
        }
        executor.execute { drainOnce() }
    }

    private fun scheduleDrainDelayed() {
        scheduler.schedule({
            synchronized(lock) {
                drainScheduled.set(false)
            }
            scheduleDrain()
        }, MIN_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun drainOnce() {
        val toSend: PendingMessage
        synchronized(lock) {
            drainScheduled.set(false)
            if (pendingMessages.isEmpty()) return
            if (!canSendNow()) {
                scheduleDrainDelayed()
                return
            }
            toSend = pendingMessages.removeFirst()
        }

        val result = postToSlack(toSend.text)
        synchronized(lock) {
            when (result) {
                PostResult.Success -> {
                    recordSend()
                    if (pendingMessages.isNotEmpty()) {
                        scheduleDrainDelayed()
                    }
                }
                PostResult.Retry -> {
                    toSend.retries++
                    if (toSend.retries <= MAX_SLACK_POST_RETRIES) {
                        pendingMessages.addFirst(toSend)
                        PushAppLogger.warn(TAG, "Slack post failed; retry ${toSend.retries}/$MAX_SLACK_POST_RETRIES")
                        scheduleDrainDelayed()
                    } else {
                        PushAppLogger.error(TAG, "Slack post dropped after $MAX_SLACK_POST_RETRIES retries")
                    }
                }
                PostResult.PermanentFailure -> {
                    pendingMessages.clear()
                    disabled = true
                    PushAppLogger.error(TAG, "Slack webhook rejected; API logging disabled for this session")
                }
            }
            Unit
        }
    }

    private enum class PostResult {
        Success,
        Retry,
        PermanentFailure,
    }

    private fun canSendNow(): Boolean {
        val now = System.currentTimeMillis()
        while (sentTimestamps.isNotEmpty() && now - sentTimestamps.first() > 60_000) {
            sentTimestamps.removeFirst()
        }
        if (sentTimestamps.size >= MAX_PER_MINUTE) return false
        if (lastSentAt > 0 && now - lastSentAt < MIN_INTERVAL_MS) return false
        return true
    }

    private fun recordSend() {
        val now = System.currentTimeMillis()
        lastSentAt = now
        sentTimestamps.addLast(now)
    }

    private fun postToSlack(text: String): PostResult {
        return try {
            val payload = JSONObject().put("text", truncate(text, 3500))
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(webhookUrl).post(body).build()
            slackClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    PushAppLogger.debug(TAG, "Slack API log posted (${response.code})")
                    return PostResult.Success
                }
                PushAppLogger.error(TAG, "Slack webhook HTTP ${response.code}")
                if (response.code in 400..499 && response.code != 429) {
                    PushAppLogger.error(
                        TAG,
                        "Slack webhook is invalid or revoked. Pass a valid slackWebhookUrl in PushApp.initialize().",
                    )
                    PostResult.PermanentFailure
                } else {
                    PostResult.Retry
                }
            }
        } catch (e: Exception) {
            PushAppLogger.error(TAG, "Failed to post API log to Slack: ${e.message}", e)
            PostResult.Retry
        }
    }

    private fun buildMessage(
        platform: String,
        method: String,
        url: String,
        requestHeaders: Map<String, String>,
        requestBody: String?,
        statusCode: Int?,
        responseHeaders: Map<String, String>?,
        responseBody: String?,
        error: String?,
    ): String {
        val sb = StringBuilder()
        sb.append("[").append(platform).append(" API] ").append(method).append(" ").append(url)
        sb.append("\n\nRequest headers:\n")
        sb.append(truncate(PushAppLogger.sanitize(formatHeaders(requestHeaders)), MAX_FIELD_CHARS))
        sb.append("\n\nRequest body:\n")
        sb.append(
            truncate(
                PushAppLogger.sanitize(requestBody?.ifBlank { "(empty)" } ?: "(none)"),
                MAX_FIELD_CHARS,
            ),
        )
        if (error != null) {
            sb.append("\n\nError:\n").append(truncate(PushAppLogger.sanitize(error), MAX_FIELD_CHARS))
        } else {
            sb.append("\n\nResponse: ").append(statusCode?.toString() ?: "?")
            sb.append("\n\nResponse headers:\n")
            sb.append(
                truncate(
                    PushAppLogger.sanitize(formatHeaders(responseHeaders ?: emptyMap())),
                    MAX_FIELD_CHARS,
                ),
            )
            sb.append("\n\nResponse body:\n")
            sb.append(
                truncate(
                    PushAppLogger.sanitize(responseBody?.ifBlank { "(empty)" } ?: "(none)"),
                    MAX_FIELD_CHARS,
                ),
            )
        }
        return sb.toString()
    }

    private fun formatHeaders(headers: Map<String, String>): String {
        if (headers.isEmpty()) return "(none)"
        return headers.entries.joinToString("\n") { (key, value) ->
            val redactedValue = if (key.equals("authorization", ignoreCase = true) ||
                key.equals("x-device-id", ignoreCase = true)
            ) {
                "***REDACTED***"
            } else {
                value
            }
            "$key: $redactedValue"
        }
    }

    private fun truncate(value: String, max: Int): String {
        if (value.length <= max) return value
        return value.take(max) + "\n…(truncated)"
    }
}
