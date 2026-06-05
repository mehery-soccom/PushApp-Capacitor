package com.mehery.pushapp

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException

/** Logs PushApp HTTP calls to Slack (rate-limited via [SlackApiLogger]). */
class ApiSlackLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host.contains("hooks.slack.com")) {
            return chain.proceed(original)
        }

        val (request, requestBody) = cloneRequestWithReadableBody(original)
        val requestHeaders = request.headers.names().associateWith { name ->
            request.headers(name).joinToString(", ")
        }

        return try {
            val response = chain.proceed(request)
            val responseHeaders = response.headers.names().associateWith { name ->
                response.headers(name).joinToString(", ")
            }
            val responseBody = response.peekBody(64 * 1024).string()
            SlackApiLogger.logApiCall(
                platform = "Android",
                method = request.method,
                url = request.url.toString(),
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                statusCode = response.code,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                error = null,
            )
            response
        } catch (e: IOException) {
            SlackApiLogger.logApiCall(
                platform = "Android",
                method = request.method,
                url = request.url.toString(),
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                statusCode = null,
                responseHeaders = null,
                responseBody = null,
                error = e.message,
            )
            throw e
        }
    }

    private fun cloneRequestWithReadableBody(request: Request): Pair<Request, String> {
        val body = request.body ?: return request to ""
        val buffer = Buffer()
        body.writeTo(buffer)
        val bodyString = buffer.readUtf8()
        val newBody = bodyString.toRequestBody(body.contentType())
        val newRequest = request.newBuilder().method(request.method, newBody).build()
        return newRequest to bodyString
    }
}
