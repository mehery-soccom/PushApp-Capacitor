package com.mehery.pushapp

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.flow.SharedFlow

class PushApp private constructor() {

    private var initialized = false
    private lateinit var context: Context
    private var serverUrl: String = ""
    private var tenant: String = ""
    private var channelId: String = ""
    private var userId: String? = null
    private var guestId: String? = null


    var lastNotificationData: Map<String, String>? = null

    private var webSocketManager: WebSocketManager? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    companion object {
        @Volatile
        private var instance: PushApp? = null

        fun getInstance(): PushApp =
            instance ?: synchronized(this) {
                instance ?: PushApp().also { instance = it }
            }
    }

    fun initialize(context: Context, identifier: String, sandbox: Boolean = false) {
        if (initialized) return
        initialized = true
        this.context = context.applicationContext

        val parts = identifier.split("$")
        if (parts.size != 2) {
            Log.e("PushApp", "Invalid identifier format, expected tenant\$channelId, got: $identifier")
            initialized = false
            return
        }
        
        tenant = parts[0]
        channelId = parts[1]

        // Set serverUrl BEFORE other operations that might need it
        serverUrl = if (sandbox) "https://$tenant.pushapp.com" else "https://$tenant.pushapp.co.in"
        Log.d("PushApp", "Server URL: $serverUrl")
        Log.d("PushApp", "Channel ID: $channelId")
        Log.d("PushApp", "Tenant: $tenant")
        Log.d("PushApp", "Sandbox: $sandbox")

        // Initialize Firebase before other operations
        // Note: Firebase might already be initialized by the app's Application class
        try {
            // Check if Firebase is already initialized
            try {
                FirebaseApp.getInstance()
                Log.d("PushApp", "Firebase already initialized")
            } catch (e: IllegalStateException) {
                // Not initialized, try to initialize
                // This might fail if google-services.json is missing or Firebase isn't configured
                try {
                    FirebaseApp.initializeApp(this.context)
                    Log.d("PushApp", "Firebase initialized successfully")
                } catch (initException: Exception) {
                    Log.w("PushApp", "Firebase initialization failed (may not be configured): ${initException.message}")
                    // Continue without Firebase - push notifications won't work but other features can
                }
            }
        } catch (e: Exception) {
            Log.w("PushApp", "Firebase check failed: ${e.message}")
            // Continue without Firebase - push notifications won't work but other features can
        }

        val prefs = context.getSharedPreferences("PushAppPrefs", Context.MODE_PRIVATE)
        val savedUserId = prefs.getString("pushapp_user_id", null)
        if (!savedUserId.isNullOrEmpty()) {
            this.userId = savedUserId
            Log.d("PushApp", "Restored userId from storage: $savedUserId")
            flushBufferedEvents()
            connectSocket()
        }

        // Register device token after Firebase is initialized
        registerDeviceToken()
    }

    // ------------------ Persistent Device ID ------------------

    private fun getPersistentDeviceId(): String {
        val prefs = context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE)
        var persistentId = prefs.getString("persistent_device_id", null)

        if (persistentId.isNullOrEmpty()) {
            val rawDeviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val timestamp = System.currentTimeMillis()
            persistentId = "${rawDeviceId}_$timestamp"
            prefs.edit().putString("persistent_device_id", persistentId).apply()
            Log.d("PushApp", "Generated persistent device ID: $persistentId")
        }

        return persistentId
    }

    private fun hasRegistered(): Boolean {
        val prefs = context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("registered", false)
    }

    private fun setRegistered() {
        val prefs = context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("registered", true).apply()
    }

    // ------------------ Page tracking ------------------

    fun setCurrentActivity(activity: Activity?) {
        currentActivityRef = if (activity != null) WeakReference(activity) else null
    }

    fun setPageName(activity: Activity?, name: String) {
        currentActivityRef = WeakReference(activity)
        sendEvent("page_open", mapOf("page" to name))
    }

    fun destroyPageName(name: String) {
        currentActivityRef = null
        sendEvent("page_closed", mapOf("page" to name))
    }

    // ------------------ Device Token ------------------

    private fun registerDeviceToken() {
        // Verify Firebase is initialized before proceeding
        try {
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            Log.w("PushApp", "Cannot register device token: Firebase not initialized. Push notifications will not be available.")
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("PushApp", "❌ Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }

            val newToken = task.result
            Log.d("PushApp", "🔑 FCM Token: $newToken")

            if (newToken.isNullOrEmpty()) {
                Log.w("PushApp", "⚠️ Empty FCM token received.")
                return@addOnCompleteListener
            }

            val prefs = context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE)
            val lastToken = prefs.getString("fcm_token", null)
            if (lastToken != null) {
                Log.d("PushApp",lastToken)
            }else{
                Log.d("PushApp","NO LAST TOKEN")
            }

            if (lastToken == newToken) {
                // ✅ Token is the same — just send to server (keep-alive)
                Log.d("PushApp", "✅ Token unchanged — calling sendTokenToServer()")
                Log.d("PushApp", "✅ Last Token — $lastToken")
                sendTokenToServer("android", newToken)
            } else if(lastToken == null){
                Log.d("PushApp", "✅ New Token - $newToken")
                sendTokenToServer("android", newToken)
            } else {
                // 🔄 Token changed — update it on server
                Log.d("PushApp", "🔄 Token changed — calling updateDeviceToken()")
                updateDeviceToken(newToken)

                // Save new token
                prefs.edit()
                    .putString("fcm_token", newToken)
                    .putBoolean("registered", true)
                    .apply()
            }
        }
    }



    fun handleDeviceToken(token: String) {
        Log.d("PushApp", "Handling device token: $token")
        sendTokenToServer("android", token)
    }

    // ------------------ Headers ------------------

    fun getDeviceHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val deviceId = getPersistentDeviceId()
        val locale = context.resources.configuration.locales[0]
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val orientation = if (screenWidth > screenHeight) "Landscape" else "Portrait"

        headers["X-Device-ID"] = deviceId
        headers["X-Screen-Resolution"] = "${screenWidth}x${screenHeight}"
        headers["X-Device-Orientation"] = orientation
        headers["X-Locale"] = locale.toString()
        headers["X-Timezone"] = java.util.TimeZone.getDefault().id
        headers["X-OS-Name"] = "ANDROID"
        headers["X-OS-Version"] = android.os.Build.VERSION.RELEASE ?: ""
        headers["X-API-Level"] = android.os.Build.VERSION.SDK_INT.toString()
        headers["X-Device-Model"] = android.os.Build.MODEL ?: ""
        headers["X-Manufacturer"] = android.os.Build.MANUFACTURER ?: ""
        headers["X-CPU-ABI"] = android.os.Build.SUPPORTED_ABIS.joinToString(", ")
        headers["X-Boot-Time"] = android.os.Build.BOOTLOADER ?: ""
        headers["X-Bundle-ID"] = context.packageName

        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            headers["X-App-Version"] = packageInfo.versionName ?: ""
            headers["X-SDK-Version"] = packageInfo.longVersionCode.toString()
        } catch (e: Exception) {
            Log.e("PushApp", "Failed to get package info: ${e.message}")
        }

        return headers
    }

    // ------------------ Registration ------------------

    private fun sendTokenToServer(platform: String, token: String) {
        if (serverUrl.isEmpty()) {
            Log.e("PushApp", "Cannot send token: PushApp not initialized")
            return
        }
        
        if (hasRegistered()) {
            Log.d("PushApp", "Device already registered. Skipping registration.")
            return
        }

        val url = "$serverUrl/pushapp/api/register"

        val json = JSONObject().apply {
            put("platform", platform)
            put("token", token)
            put("device_id", getPersistentDeviceId())
            put("channel_id", channelId)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(body)

        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
            Log.d("PushApp", "Header: $k = $v")
        }

        Log.d("PushApp", "SendToken API URL: $url")
        Log.d("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Failed to send token to server: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    Log.d("PushApp", "SendToken API success, code=${response.code}")
                    Log.d("PushApp", "Response Body: $responseBody")
                    setRegistered()

                    try {
                        val responseJson = JSONObject(responseBody ?: "{}")
                        val device = responseJson.optJSONObject("device")
                        guestId = device?.optString("user_id")
                        Log.d("PushApp", "Guest ID: $guestId")
                    } catch (e: Exception) {
                        Log.e("PushApp", "Error parsing sendToken response: ${e.message}")
                    }

                    flushBufferedEvents()
                    sendEvent("app_open", emptyMap())
                } else {
                    Log.e("PushApp", "SendToken API failed: HTTP ${response.code}")
                    Log.e("PushApp", "Response Body: $responseBody")
                }
                response.close()
            }
        })
    }

    // ------------------ Login ------------------

    fun login(userId: String) {
        if (serverUrl.isEmpty()) {
            Log.e("PushApp", "PushApp not initialized. Call initialize() first.")
            return
        }
        
        this.userId = userId
        val url = "$serverUrl/pushapp/api/register/user"

        val json = JSONObject().apply {
            put("user_id", userId)
            put("device_id", getPersistentDeviceId())
            put("channel_id", channelId)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(body)

        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
            Log.d("PushApp", "Header: $k = $v")
        }

        Log.d("PushApp", "Login API URL: $url")
        Log.d("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Login API failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    // Persist userId in SharedPreferences
                    val prefs = context.getSharedPreferences("PushAppPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("pushapp_user_id", userId).apply()
                    Log.d("PushApp", "Login API success, code=${response.code}")
                    Log.d("PushApp", "Response Body: $responseBody")

                    flushBufferedEvents()
                    connectSocket()
                } else {
                    Log.e("PushApp", "Login API failed: HTTP ${response.code}")
                    Log.e("PushApp", "Response Body: $responseBody")
                }
                response.close()
            }
        })
    }

    // ------------------ Send Event ------------------

    fun sendEvent(eventName: String, eventData: Map<String, Any>) {
        if (serverUrl.isEmpty()) {
            Log.e("PushApp", "PushApp not initialized. Call initialize() first.")
            bufferEvent(eventName, eventData)
            return
        }
        
        val userIdToUse = userId ?: guestId
        if (userIdToUse == null) {
            bufferEvent(eventName, eventData)
            return
        }

        val url = "$serverUrl/pushapp/api/v1/event"

        val json = JSONObject().apply {
            put("user_id", userIdToUse)
            put("channel_id", channelId)
            put("event_name", eventName)
            put("event_data", JSONObject(eventData))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(requestBody)

        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
            Log.d("PushApp", "Header: $k = $v")
        }

        Log.d("PushApp", "Event Triggered: $eventName")
        Log.d("PushApp", "Event Data: $eventData")
        Log.d("PushApp", "Event URL: $url")
        Log.d("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Event send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    Log.d("PushApp", "Event sent successfully")
                    Log.d("PushApp", "Response Body: $bodyStr")
                    Handler(Looper.getMainLooper()).postDelayed({
                        userId?.let { pollForInApp(it) }
                    }, 2000)
                } else {
                    Log.e("PushApp", "Failed to send event: ${response.code}")
                    Log.e("PushApp", "Response Body: $bodyStr")
                }
                response.close()
            }
        })
    }

    fun updateDeviceToken(token: String) {
        Log.d("PushApp", "🔄 updateDeviceToken() called")

        val url = "$serverUrl/pushapp/api/update/token"
        val deviceId = getPersistentDeviceId()
        val contactId = "${userId}_${deviceId}"

        try {
            val json = JSONObject().apply {
                put("contact_id", contactId)
                put("token", token)
                put("channel_id", channelId)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder().url(url).post(requestBody)

            // Add headers (optional — use your own method if you have one)
            getDeviceHeaders().forEach { (k, v) ->
                requestBuilder.addHeader(k, v)
                Log.d("PushApp", "Header: $k = $v")
            }

            Log.d("PushApp", "URL: $url")
            Log.d("PushApp", "Request Body: ${json.toString(2)}")

            val request = requestBuilder.build()
            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("PushApp", "🔥 Error updating device token: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful) {
                        Log.d("PushApp", "✅ Token updated successfully on server.")
                        Log.d("PushApp", "Response Body: $bodyStr")
                    } else {
                        Log.e("PushApp", "❌ Failed to update token: ${response.code}")
                        Log.e("PushApp", "Response Body: $bodyStr")
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e("PushApp", "🔥 Exception in updateDeviceToken: ${e.message}")
        }
    }


    fun ackInApp(messageId: String) {
        val url = "$serverUrl/pushapp/api/v1/notification/in-app/ack"

        val json = JSONObject().apply {
            put("contact_id", "${userId}_${getPersistentDeviceId()}")
            put("messageId", messageId)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(requestBody)

        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
            Log.d("PushApp", "Header: $k = $v")
        }

        Log.d("PushApp", "Ack Triggered")
        Log.d("PushApp", "Ack URL: $url")
        Log.d("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Ack send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    Log.d("PushApp", "Ack sent successfully")
                    Log.d("PushApp", "Response Body: $bodyStr")
                } else {
                    Log.e("PushApp", "Failed to send ack: ${response.code}")
                    Log.e("PushApp", "Response Body: $bodyStr")
                }
                response.close()
            }
        })
    }


    fun trackInAppEvent(
        messageId: String,
        event: String,
        filterId: String? = null,
        ctaId: String? = null
    ) {
        val url = "$serverUrl/pushapp/api/v1/notification/in-app/track"

        val json = JSONObject().apply {
            put("messageId", messageId)
            put("event", event)
            filterId?.let { put("filterId", it) }
            ctaId?.let {
                put("data", JSONObject().apply {
                    put("ctaId", it)
                })
            }
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(requestBody)

        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
            Log.d("PushApp", "Header: $k = $v")
        }

        Log.d("PushApp", "Track Triggered: $event")
        Log.d("PushApp", "Track URL: $url")
        Log.d("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Track send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    Log.d("PushApp", "Track sent successfully")
                    Log.d("PushApp", "Response Body: $bodyStr")
                } else {
                    Log.e("PushApp", "Failed to send track: ${response.code}")
                    Log.e("PushApp", "Response Body: $bodyStr")
                }
                response.close()
            }
        })
    }

    fun trackNotificationEvent(
        clickToken: String,
        event: String,        // "opened" or "cta"
        ctaId: String? = null
    ) {
        val url = "$serverUrl/pushapp/api/v1/notification/push/track"

        val json = JSONObject().apply {
            put("t", clickToken)
            put("event", event)
            ctaId?.let {
                put("data", JSONObject().apply {
                    put("ctaId", it)
                })
            }
        }

        println(url)

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(requestBody)

        // Add device headers (same as trackInAppEvent)
        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
            Log.d("PushApp", "Header: $k = $v")
        }

        Log.d("PushApp", "Notification Track Triggered: $event")
        Log.d("PushApp", "Track URL: $url")
        Log.d("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Notification Track failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    Log.d("PushApp", "Notification Track sent successfully")
                    Log.d("PushApp", "Response Body: $bodyStr")
                } else {
                    Log.e("PushApp", "Failed to send Notification Track: ${response.code}")
                    Log.e("PushApp", "Response Body: $bodyStr")
                }
                response.close()
            }
        })
    }



    // ------------------ Poll In-App ------------------

    private fun pollForInApp(userId: String) {
        val url = "$serverUrl/pushapp/api/v1/notification/in-app/poll"

        val json = JSONObject().apply {
            put("contact_id", "${userId}_${getPersistentDeviceId()}")
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(body)

        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
            Log.d("PushApp", "Header: $k = $v")
        }

        Log.d("PushApp", "Polling In-App URL: $url")
        Log.d("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PushApp", "Poll in-app failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    Log.d("PushApp", "Poll in-app successful, code=${response.code}")
                    Log.d("PushApp", "Response Body: $responseBody")

                    if (!responseBody.isNullOrEmpty()) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val success = jsonResponse.optBoolean("success", false)
                            Log.d("PushApp", "JSON Body: $jsonResponse")
                            if (success) {
                                val results = jsonResponse.optJSONArray("results")
                                if (results != null && results.length() > 0) {
                                    val inAppItems = mutableListOf<Map<String, Any>>()

                                    for (i in 0 until results.length()) {
                                        val item = results.getJSONObject(i)
                                        val messageId = item.optString("messageId")
                                        val filterId = item.optString("filterId")
                                        val template = item.optJSONObject("template")
                                        val style = template?.optJSONObject("style")
                                        val event = item?.optJSONObject("event")
                                        val eventData = event?.optJSONObject("event_data")

                                        val layoutCode = style?.optString("code") ?: continue

                                        // 🔹 Tooltip handling (generic target)
                                        if (layoutCode.equals("tooltip", ignoreCase = true)) {
                                            val target = item
                                                .optJSONObject("event")
                                                ?.optJSONObject("event_data")
                                                ?.optString("compare")
                                                ?.takeIf { it.isNotBlank() }
                                                ?: (template?.optString("code") ?: "default")

                                            val title = style?.optString("line_1").orEmpty()
                                            val message = style?.optString("line_2").orEmpty()

                                            val iconRaw = style?.optString("line1_icon").orEmpty()
                                            val iconDecoded = HtmlCompat.fromHtml(
                                                iconRaw,
                                                HtmlCompat.FROM_HTML_MODE_LEGACY
                                            ).toString()

                                            val bgColor = style?.optString("bg_color").orEmpty()
                                            val width = style?.optInt("width", 60) ?: 60

                                            // Font size and color
                                            val line1FontSize = style?.optInt("line1_font_size", 14) ?: 14
                                            val line2FontSize = style?.optInt("line2_font_size", 12) ?: 12
                                            val line1FontColor = style?.optString("line1_font_color").takeIf { it?.isNotBlank() == true }
                                            val line2FontColor = style?.optString("line2_font_color").takeIf { it?.isNotBlank() == true }

                                            Log.d("PushApp", "Processing tooltip for target=$target, title=$title")

                                            // Send ACK for the message
                                            ackInApp(messageId)

                                            // Check if there's a registered tooltip target for this target ID
                                            val tooltipTarget = PlaceholderViewManager.getTooltipTarget(target)
                                            Log.d("PushApp", "Looking for tooltip target: $target")
                                            Log.d("PushApp", "Available tooltip targets: ${PlaceholderViewManager.getAllTooltipTargetIds()}")
                                            
                                            if (tooltipTarget != null) {
                                                // Display tooltip at registered target position
                                                Log.d("PushApp", "✅ Found registered tooltip target: $target")
                                                Log.d("PushApp", "Target coordinates: x=${tooltipTarget.x}, y=${tooltipTarget.y}, width=${tooltipTarget.width}, height=${tooltipTarget.height}")
                                                currentActivityRef?.get()?.let { activity ->
                                                    activity.runOnUiThread {
                                                        InAppDisplay(activity).showTooltipAtPosition(
                                                            tooltipTarget = tooltipTarget,
                                                            messageId = messageId,
                                                            filterId = filterId,
                                                            title = title,
                                                            message = message,
                                                            icon = iconDecoded.ifBlank { null },
                                                            bgColor = bgColor,
                                                            widthPercent = width,
                                                            line1FontSize = line1FontSize,
                                                            line2FontSize = line2FontSize,
                                                            line1FontColor = line1FontColor,
                                                            line2FontColor = line2FontColor,
                                                            activity = activity
                                                        )
                                                    }
                                                }
                                            } else {
                                                // Fallback to emitting tooltip event (for other tooltip systems)
                                                Log.d("PushApp", "No registered tooltip target found for: $target, emitting tooltip event")
                                                PushAppEvents.emitTooltip(
                                                    TooltipEvent(
                                                        target = target,
                                                        title = title,
                                                        message = message,
                                                        icon = iconDecoded.ifBlank { null },
                                                        bgColor = bgColor,
                                                        widthPercent = width,
                                                        line1FontSize = line1FontSize,
                                                        line2FontSize = line2FontSize,
                                                        line1FontColor = line1FontColor,
                                                        line2FontColor = line2FontColor
                                                    )
                                                )
                                            }
                                            continue
                                        }

                                        // 🔹 Non-tooltip in-app
                                        val html = style?.optString("html") ?: continue
                                        val type = style?.optString("type") ?: "roadblock"
                                        val vertical_align = style?.optString("vertical_align") ?: "flex-end"
                                        val horizontal_align = style?.optString("horizontal_align") ?: "flex-end"
                                        val draggable = style?.optBoolean("draggable") ?: false
                                        var placeholderId = eventData?.optString("compare") ?: ""

                                        // 🔹 Check if placeholderId exists - dispatch to placeholder instead of showing as banner/roadblock
                                        if (placeholderId.isNotBlank()) {
                                            Log.d("PushApp", "Dispatching to placeholder: $placeholderId")
                                            // Send ack for the message
                                            ackInApp(messageId)
                                            // Dispatch HTML to placeholder
                                            PushAppPlaceholderManager.dispatchPlaceholderContent(
                                                placeholderId,
                                                messageId,
                                                html
                                            )
                                            continue
                                        }

                                        inAppItems.add(
                                            mapOf(
                                                "code" to layoutCode,
                                                "html" to html,
                                                "type" to type,
                                                "vertical_align" to vertical_align,
                                                "draggable" to draggable,
                                                "horizontal_align" to horizontal_align,
                                                "placeholderId" to placeholderId,
                                                "messageId" to messageId,
                                                "filterId" to filterId
                                            )
                                        )

                                        Log.d("PushApp", "In App Items: $inAppItems")
                                    }

                                    if (inAppItems.isNotEmpty()) {
                                        try {
                                            Log.d("PushApp", "Displaying ${inAppItems.size} in-app items sequentially")
                                            val inAppDisplay = InAppDisplay(currentActivityRef?.get()!!)
                                            inAppDisplay.showInApps(inAppItems)
                                        } catch (e: Exception) {
                                            Log.e("PushApp", "Error showing in-app: ${e.message}")
                                        }
                                    } else {
                                        Log.w("PushApp", "No valid in-app items to display")
                                    }
                                } else {
                                    Log.w("PushApp", "No in-app notification results found")
                                }
                            } else {
                                Log.w("PushApp", "Poll in-app returned success=false")
                            }
                        } catch (e: Exception) {
                            Log.e("PushApp", "Error parsing poll in-app response: ${e.message}")
                        }
                    } else {
                        Log.w("PushApp", "Empty poll in-app response body")
                    }
                } else {
                    Log.e("PushApp", "Poll in-app failed: HTTP ${response.code}")
                    Log.e("PushApp", "Response Body: $responseBody")
                }
                response.close()
            }


        })
    }



    // ------------------ Helper Methods ------------------

    private fun bufferEvent(eventName: String, eventData: Map<String, Any>) {
        val sharedPrefs = context.getSharedPreferences("pushapp", Context.MODE_PRIVATE)
        val bufferJsonArray = JSONArray(sharedPrefs.getString("event_buffer", "[]"))
        val eventJson = JSONObject()
        eventJson.put("event_name", eventName)
        eventJson.put("event_data", JSONObject(eventData))
        bufferJsonArray.put(eventJson)
        sharedPrefs.edit().putString("event_buffer", bufferJsonArray.toString()).apply()
        Log.d("PushApp", "Buffered event: $eventName")
    }

    private fun flushBufferedEvents() {
        val userIdToUse = userId ?: guestId ?: return
        val sharedPrefs = context.getSharedPreferences("pushapp", Context.MODE_PRIVATE)
        val bufferJsonArray = JSONArray(sharedPrefs.getString("event_buffer", "[]"))
        if (bufferJsonArray.length() == 0) return

        Log.d("PushApp", "Flushing ${bufferJsonArray.length()} buffered events")
        for (i in 0 until bufferJsonArray.length()) {
            val item = bufferJsonArray.getJSONObject(i)
            val eventName = item.getString("event_name")
            val eventData = item.getJSONObject("event_data")
            val map = mutableMapOf<String, Any>()
            eventData.keys().forEach { key -> map[key] = eventData.get(key) }
            sendEvent(eventName, map)
        }
        sharedPrefs.edit().remove("event_buffer").apply()
    }

    // ------------------ Socket ------------------

    private fun connectSocket() {
        val id = userId ?: guestId ?: run {
            Log.d("PushApp", "No userId or guestId available to connect socket")
            return
        }

        webSocketManager?.disconnect()
        webSocketManager = WebSocketManager(id, tenant) { data ->
            Log.d("PushApp", "WebSocket received data: $data")
            handleSocketMessage(data)
        }
        webSocketManager?.connect()
    }

    fun handleNotification(data: Map<String, String>) {
        Log.d("PushApp", "Handling notification data: $data")
        // Your in-app notification or callback handling here
    }

    private fun handleSocketMessage(data: Map<String, Any>) {
        val messageType = (data["data"] as? Map<String, Any>)?.get("message_type") as? String
        if (messageType == "rule_triggered") {
            val ruleId = (data["data"] as? Map<String, Any>)?.get("rule_id") as? String
            ruleId?.let { pollForInApp(it) }
        } else {
            handleSocketNotification(data.mapValues { it.value.toString() })
        }
    }

    fun handleSocketNotification(data: Map<String, String>) {
        val activity = currentActivityRef?.get() ?: run {
            Log.w("PushApp", "No active activity available to show in-app notification")
            return
        }

        val notificationType = data["type"]
        val rawDataJson = data["data"]

        if (notificationType == "in_app" && !rawDataJson.isNullOrEmpty()) {
            try {
                val fullData = JSONObject(rawDataJson)

                // Support both single template or an array of templates
                val resultsArray = fullData.optJSONArray("results")
                val inAppItems = mutableListOf<Map<String, Any>>()

                if (resultsArray != null && resultsArray.length() > 0) {
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.getJSONObject(i)
                        val template = item.optJSONObject("template") ?: continue
                        val style = template.optJSONObject("style") ?: continue

                        val code = style.optString("code")
                        val html = style.optString("html")
                        val type = style.optString("type").ifEmpty {
                            // Infer type if missing: default to roadblock
                            "roadblock"
                        }

                        if (html.isNotEmpty()) {
                            inAppItems.add(
                                mapOf(
                                    "code" to code,
                                    "html" to html,
                                    "type" to type
                                )
                            )
                        }
                    }
                } else {
                    // Fallback to single object in "data"
                    val code = fullData.optString("code")
                    val html = fullData.optString("html")
                    val type = fullData.optString("type").ifEmpty { "roadblock" }

                    if (html.isNotEmpty()) {
                        inAppItems.add(
                            mapOf(
                                "code" to code,
                                "html" to html,
                                "type" to type
                            )
                        )
                    }
                }

                if (inAppItems.isNotEmpty()) {
                    activity.runOnUiThread {
                        try {
                            val inAppDisplay = InAppDisplay(activity)
                            inAppDisplay.showInApps(inAppItems) // Sequential display
                        } catch (e: Exception) {
                            Log.e("PushApp", "Error showing in-app notification: ${e.message}")
                        }
                    }
                } else {
                    Log.w("PushApp", "No valid in-app items found in socket notification")
                }

            } catch (e: Exception) {
                Log.e("PushApp", "Failed to parse in-app data: ${e.message}")
            }
        } else {
            Log.d("PushApp", "Notification is not in-app or data is missing")
        }
    }


    // ------------------ JSON Helpers ------------------

    private fun JSONObject.toMap(): Map<String, Any> = keys().asSequence().associateWith { key ->
        when (val value = this[key]) {
            is JSONArray -> List(value.length()) { i -> value[i] }
            is JSONObject -> value.toMap()
            else -> value
        }
    }
}

data class TooltipEvent(
    val target: String,
    val title: String,
    val message: String,
    val icon: String? = null,
    val bgColor: String? = null,       // hex color string from server
    val widthPercent: Int = 60,        // tooltip width as % of screen
    val line1FontSize: Int = 14,       // font size for title
    val line2FontSize: Int = 12,       // font size for message
    val line1FontColor: String? = null, // hex color string
    val line2FontColor: String? = null  // hex color string
)


object PushAppEvents {
    private val _tooltipFlow = MutableSharedFlow<TooltipEvent>(extraBufferCapacity = 1)
    val tooltipFlow: SharedFlow<TooltipEvent> = _tooltipFlow

    fun emitTooltip(event: TooltipEvent) {
        _tooltipFlow.tryEmit(event)
    }
}

