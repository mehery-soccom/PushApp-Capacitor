package com.mehery.pushapp

import okhttp3.*
import okio.ByteString
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val userId: String,
    private val tenant: String,
    private val onMessage: (Map<String, Any>) -> Unit
) : WebSocketListener() {

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val url: String
        get() {
            val baseUrl = "wss://$tenant.pushapp.co.in/pushapp"
//            val baseUrl = "wss://1b790b9b08a8.ngrok-free.app/pushapp"
//            Log.d("WebSocketManager", "WebSocket URL: $baseUrl")
            return baseUrl
        }

    fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()
        webSocket = client.newWebSocket(request, this)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("WebSocketManager", "WebSocket connected")
        sendAuth()
    }

    private fun sendAuth() {
        val authJson = JSONObject()
        authJson.put("type", "auth")
        authJson.put("userId", userId)
        webSocket?.send(authJson.toString())
        Log.d("WebSocketManager", "Auth sent: $authJson")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("WebSocketManager", "Message received: $text")
        try {
            val json = JSONObject(text)
            val map = json.toMap()
            onMessage(map)
        } catch (e: Exception) {
            Log.e("WebSocketManager", "JSON parsing error: ${e.message}")
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        // Optionally handle binary messages if your server sends any
        Log.d("WebSocketManager", "Binary message received: ${bytes.hex()}")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("WebSocketManager", "Closing WebSocket: $code / $reason")
        webSocket.close(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("WebSocketManager", "WebSocket closed: $code / $reason")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("WebSocketManager", "WebSocket failure: ${t.message}")
        reconnectWithDelay()
    }

    private fun reconnectWithDelay() {
        Log.d("WebSocketManager", "Attempting reconnect in 5 seconds")
        Thread.sleep(5000)
        connect()
    }
}

// Helper to convert JSONObject to Map<String, Any>
fun JSONObject.toMap(): Map<String, Any> = keys().asSequence().associateWith { key ->
    when (val value = this[key]) {
        is JSONObject -> value.toMap()
        else -> value
    }
}
