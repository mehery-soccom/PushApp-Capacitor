package com.mehery.pushapp

import android.util.Log

/**
 * SDK logger with release-safe defaults.
 * - Release builds: [Level.ERROR] only (errors/warnings, redacted).
 * - Debug builds: [Level.DEBUG] when [configure] is called with `debug = true`.
 */
object PushAppLogger {
    enum class Level {
        NONE,
        ERROR,
        DEBUG,
    }

    private val sensitiveJsonKeyPattern = Regex(
        """"(token|fcm_token|apns_token|user_id|device_id|guest_id|channel_id|code|password|email|phone|authorization)"\s*:\s*"[^"]*"""",
        RegexOption.IGNORE_CASE,
    )
    private val sensitiveHeaderPattern = Regex(
        """(?i)(authorization|x-device-id)\s*[=:]\s*\S+""",
    )
    private val bearerPattern = Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE)

    @Volatile
    private var level: Level = Level.ERROR

    @JvmStatic
    fun configure(debug: Boolean, appDebuggable: Boolean = BuildConfig.DEBUG) {
        level = if (debug && appDebuggable) Level.DEBUG else Level.ERROR
        if (level == Level.DEBUG) {
            Log.d("PushApp", "PushAppLogger: verbose logging enabled (debugMode=true)")
        }
    }

    @JvmStatic
    fun isDebugEnabled(): Boolean = level == Level.DEBUG

    @JvmStatic
    fun logApiCall(
        method: String,
        url: String,
        requestBody: String?,
        statusCode: Int?,
        responseBody: String?,
        error: String? = null,
    ) {
        if (level != Level.DEBUG) return
        debug("PushApp", "API $method $url")
        if (!requestBody.isNullOrBlank()) {
            debug("PushApp", "Request Body: $requestBody")
        }
        if (error != null) {
            error("PushApp", "API error: $error")
            return
        }
        if (statusCode != null) {
            debug("PushApp", "Response ($statusCode): ${responseBody.orEmpty()}")
        }
    }

    @JvmStatic
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (level == Level.NONE) return
        val sanitized = sanitize(message)
        if (throwable != null) {
            Log.e(tag, sanitized, throwable)
        } else {
            Log.e(tag, sanitized)
        }
    }

    @JvmStatic
    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        if (level == Level.NONE) return
        val sanitized = sanitize(message)
        if (level == Level.DEBUG) {
            if (throwable != null) Log.w(tag, sanitized, throwable) else Log.w(tag, sanitized)
        } else {
            error(tag, message, throwable)
        }
    }

    @JvmStatic
    fun debug(tag: String, message: String) {
        if (level != Level.DEBUG) return
        Log.d(tag, sanitize(message))
    }

    /** Logs the raw push token when verbose debug logging is enabled (never redacted). */
    @JvmStatic
    fun logPushToken(tag: String, label: String, token: String) {
        if (level != Level.DEBUG || token.isEmpty()) return
        Log.d(tag, "$label: $token")
    }

    @JvmStatic
    fun sanitize(message: String): String {
        var result = message
        result = sensitiveJsonKeyPattern.replace(result) { match ->
            val key = match.groupValues[1]
            "\"$key\":\"***REDACTED***\""
        }
        result = sensitiveHeaderPattern.replace(result) { match ->
            val parts = match.value.split(Regex("""\s*[=:]\s*"""), limit = 2)
            if (parts.size == 2) "${parts[0]}=***REDACTED***" else match.value
        }
        result = bearerPattern.replace(result, "Bearer ***REDACTED***")
        return result
    }
}
