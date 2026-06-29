package com.mehery.pushapp

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.flow.SharedFlow

class PushApp private constructor() {

    private enum class SdkLifecycle {
        NOT_INITIALIZED,
        INITIALIZED,
        REGISTERED,
        LOGGED_IN,
    }

    private var initialized = false
    private var lifecycleState = SdkLifecycle.NOT_INITIALIZED
    private lateinit var context: Context
    private var serverUrl: String = ""
    private var sandbox: Boolean = false
    private var tenant: String = ""
    private var channelId: String = ""
    private var userId: String? = null
    private var guestId: String? = null


    var lastNotificationData: Map<String, String>? = null

    private var webSocketManager: WebSocketManager? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var lastAppOpenSentAt = 0L

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(ApiSlackLoggingInterceptor())
        .build()

    companion object {
        private const val APP_OPEN_DEBOUNCE_MS = 3000L
        private const val PREF_GUEST_ID = "pushapp_guest_id"

        @Volatile
        private var instance: PushApp? = null

        fun getInstance(): PushApp =
            instance ?: synchronized(this) {
                instance ?: PushApp().also { instance = it }
            }
    }

    fun initialize(
        context: Context,
        appId: String,
        sandbox: Boolean = false,
        slackWebhookUrl: String? = null,
        debugMode: Boolean = false,
    ): Boolean {
        PushAppLogger.configure(debugMode)
        SlackApiLogger.configure(slackWebhookUrl)
        if (initialized) {
            PushAppLogger.debug("PushApp", "Already initialized; updated logging config")
            return lifecycleState != SdkLifecycle.NOT_INITIALIZED
        }

        this.context = context.applicationContext

        // App ID is the full channel id (e.g. demo_1763369170735). Tenant is the prefix before the first '_'.
        val u = appId.indexOf('_')
        if (u <= 0 || u >= appId.length - 1) {
            PushAppLogger.error(
                "PushApp",
                "Initialize failed: invalid app id (expected tenant_prefix before first '_', e.g. demo_1763369170735, got: $appId)",
            )
            initialized = false
            lifecycleState = SdkLifecycle.NOT_INITIALIZED
            return false
        }

        initialized = true
        tenant = appId.substring(0, u)
        channelId = appId
        this.sandbox = sandbox

        // Set serverUrl BEFORE other operations that might need it
        serverUrl = if (sandbox) "https://$tenant.pushapp.co.in" else "https://$tenant.pushapp.ai"
        lifecycleState = SdkLifecycle.INITIALIZED
        PushAppLogger.debug("PushApp", "SDK initialized — next: call register() with push token, then login()")
        PushAppLogger.debug("PushApp", "Server URL: $serverUrl")
        PushAppLogger.debug("PushApp", "Channel ID: $channelId")
        PushAppLogger.debug("PushApp", "Tenant: $tenant")
        PushAppLogger.debug("PushApp", "Sandbox: $sandbox")

        // Initialize Firebase before other operations
        // Note: Firebase might already be initialized by the app's Application class
        try {
            // Check if Firebase is already initialized
            try {
                FirebaseApp.getInstance()
                PushAppLogger.debug("PushApp", "Firebase already initialized")
            } catch (e: IllegalStateException) {
                // Not initialized, try to initialize
                // This might fail if google-services.json is missing or Firebase isn't configured
                try {
                    FirebaseApp.initializeApp(this.context)
                    PushAppLogger.debug("PushApp", "Firebase initialized successfully")
                } catch (initException: Exception) {
                    PushAppLogger.warn("PushApp", "Firebase initialization failed (may not be configured): ${initException.message}")
                    // Continue without Firebase - push notifications won't work but other features can
                }
            }
        } catch (e: Exception) {
            PushAppLogger.warn("PushApp", "Firebase check failed: ${e.message}")
            // Continue without Firebase - push notifications won't work but other features can
        }

        val prefs = context.getSharedPreferences("PushAppPrefs", Context.MODE_PRIVATE)
        val devicePrefs = context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE)
        guestId = devicePrefs.getString(PREF_GUEST_ID, null)?.takeIf { it.isNotEmpty() }

        val savedUserId = prefs.getString("pushapp_user_id", null)
        if (!savedUserId.isNullOrEmpty()) {
            this.userId = savedUserId
            PushAppLogger.debug("PushApp", "Restored userId from storage")
        }

        if (hasRegistered()) {
            lifecycleState = if (userId != null) SdkLifecycle.LOGGED_IN else SdkLifecycle.REGISTERED
            PushAppLogger.debug("PushApp", "Restored session: device already registered")
            flushBufferedEvents()
            if (userId != null || guestId != null) {
                connectSocket()
            }
            notifyAppOpen()
        }

        devicePrefs.getString("fcm_token", null)?.takeIf { it.isNotEmpty() }?.let { cached ->
            PushAppLogger.logPushToken("PushApp", "FCM token (stored)", cached)
        }
        registerDeviceToken()

        return true
    }

    fun onAppForegrounded() {
        notifyAppOpen()
    }

    /**
     * Android 13+ requires runtime POST_NOTIFICATIONS or system notifications are silently dropped.
     */
    fun ensureNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            PushAppLogger.debug("PushApp", "POST_NOTIFICATIONS already granted")
            return
        }
        PushAppLogger.debug("PushApp", "Requesting POST_NOTIFICATIONS permission")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1001,
        )
    }

    private fun notifyAppOpen() {
        if (!initialized || serverUrl.isEmpty()) return
        if (lifecycleState.ordinal < SdkLifecycle.REGISTERED.ordinal) return
        if (userId == null && guestId == null) return

        val now = System.currentTimeMillis()
        if (now - lastAppOpenSentAt < APP_OPEN_DEBOUNCE_MS) return
        lastAppOpenSentAt = now

        PushAppLogger.debug("PushApp", "Sending app_open event")
        sendEvent("app_open", mapOf("compare" to channelId))
    }

    private fun persistGuestId(guest: String?) {
        guestId = guest?.takeIf { it.isNotEmpty() }
        val editor = context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE).edit()
        if (guestId != null) {
            editor.putString(PREF_GUEST_ID, guestId)
        } else {
            editor.remove(PREF_GUEST_ID)
        }
        editor.apply()
    }

    private fun logSequenceError(required: String, actual: SdkLifecycle) {
        PushAppLogger.error(
            "PushApp",
            "SDK call order violation: $required (current state: $actual). " +
                "Required order: initialize() → register() → login()",
        )
    }

    fun isInitialized(): Boolean = lifecycleState != SdkLifecycle.NOT_INITIALIZED

    // ------------------ Persistent Device ID ------------------

    private fun mapToJsonObject(map: Map<String, Any>): JSONObject {
        val j = JSONObject()
        map.forEach { (k, v) ->
            j.put(k, when (v) {
                is Map<*, *> -> mapToJsonObject(v as Map<String, Any>)
                is List<*> -> JSONArray(v)
                else -> v
            })
        }
        return j
    }

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
            PushAppLogger.debug("PushApp", "Generated persistent device ID: $persistentId")
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
            PushAppLogger.warn("PushApp", "Cannot register device token: Firebase not initialized. Push notifications will not be available.")
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                PushAppLogger.warn("PushApp", "❌ Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }

            val newToken = task.result
            PushAppLogger.logPushToken("PushApp", "FCM token", newToken.orEmpty())

            if (newToken.isNullOrEmpty()) {
                PushAppLogger.warn("PushApp", "⚠️ Empty FCM token received.")
                return@addOnCompleteListener
            }

            val prefs = context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE)
            val lastToken = prefs.getString("fcm_token", null)
            if (lastToken != null) {
                PushAppLogger.debug("PushApp", "Cached FCM token present")
            }else{
                PushAppLogger.debug("PushApp", "No cached FCM token")
            }

            if (lastToken == newToken) {
                if (hasRegistered()) {
                    PushAppLogger.debug("PushApp", "Token unchanged and device already registered — skipping register API")
                    return@addOnCompleteListener
                }
                PushAppLogger.debug("PushApp", "Token unchanged — calling sendTokenToServer()")
                sendTokenToServer("android", newToken)
            } else if(lastToken == null){
                PushAppLogger.debug("PushApp", "New FCM token — registering device")
                sendTokenToServer("android", newToken)
            } else {
                // 🔄 Token changed — update it on server
                PushAppLogger.debug("PushApp", "🔄 Token changed — calling updateDeviceToken()")
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
        PushAppLogger.logPushToken("PushApp", "FCM token (onNewToken)", token)
        PushAppLogger.debug("PushApp", "Handling device token update")
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
            PushAppLogger.error("PushApp", "Failed to get package info: ${e.message}")
        }

        return headers
    }

    // ------------------ Registration ------------------

    private fun sendTokenToServer(platform: String, token: String) {
        if (serverUrl.isEmpty()) {
            PushAppLogger.error("PushApp", "Cannot send token: PushApp not initialized")
            return
        }
        if (lifecycleState.ordinal < SdkLifecycle.REGISTERED.ordinal) {
            PushAppLogger.warn(
                "PushApp",
                "Token received but register() was not called yet — caching locally. " +
                    "Call register() after initialize() to register with the server.",
            )
            context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE).edit()
                .putString("fcm_token", token)
                .apply()
            PushAppLogger.logPushToken("PushApp", "FCM token (cached)", token)
            return
        }
        if (hasRegistered()) {
            PushAppLogger.debug("PushApp", "Device already registered. Skipping registration.")
            return
        }
        postDeviceRegister(platform, token, callback = null)
    }

    /**
     * POST push token to `/pushapp/api/device/register`.
     * Skips the API when the device is already registered with the same FCM token.
     * Call from Capacitor after you obtain FCM (Android) or APNs/FCM (iOS) token in JS/native.
     */
    fun register(token: String, callback: (Boolean) -> Unit) {
        if (lifecycleState == SdkLifecycle.NOT_INITIALIZED || serverUrl.isEmpty()) {
            logSequenceError("register() requires initialize() first", lifecycleState)
            Handler(Looper.getMainLooper()).post { callback(false) }
            return
        }
        val prefs = context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE)
        val tokenToSend = token.ifBlank {
            prefs.getString("fcm_token", null).orEmpty()
        }
        if (tokenToSend.isBlank()) {
            PushAppLogger.error("PushApp", "register() failed: push token is empty (wait for FCM token or pass fcmToken from JS)")
            Handler(Looper.getMainLooper()).post { callback(false) }
            return
        }
        if (token.isBlank()) {
            PushAppLogger.debug("PushApp", "Using cached FCM token for register()")
        }
        PushAppLogger.logPushToken("PushApp", "FCM token (register)", tokenToSend)
        if (hasRegistered()) {
            val savedToken = prefs.getString("fcm_token", null)
            if (savedToken == tokenToSend) {
                PushAppLogger.debug("PushApp", "Device already registered with same token — skipping register API")
                if (lifecycleState == SdkLifecycle.INITIALIZED) {
                    lifecycleState = SdkLifecycle.REGISTERED
                }
                if (guestId == null) {
                    guestId = prefs.getString(PREF_GUEST_ID, null)?.takeIf { it.isNotEmpty() }
                }
                notifyAppOpen()
                Handler(Looper.getMainLooper()).post { callback(true) }
                return
            }
            PushAppLogger.debug("PushApp", "FCM token changed — re-registering device")
        }
        postDeviceRegister("android", tokenToSend, callback = callback)
    }

    private fun postDeviceRegister(platform: String, token: String, callback: ((Boolean) -> Unit)?) {
        val url = "$serverUrl/pushapp/api/device/register"

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
            PushAppLogger.debug("PushApp", "Header: $k = $v")
        }

        PushAppLogger.debug("PushApp", "SendToken API URL: $url")
        PushAppLogger.debug("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Failed to send token to server: ${e.message}")
                callback?.let { cb ->
                    Handler(Looper.getMainLooper()).post { cb(false) }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val success = response.isSuccessful
                if (success) {
                    PushAppLogger.debug("PushApp", "SendToken API success, code=${response.code}")
                    PushAppLogger.debug("PushApp", "Response Body: $responseBody")
                    setRegistered()
                    lifecycleState = SdkLifecycle.REGISTERED
                    PushAppLogger.debug("PushApp", "SDK registered — next: call login()")
                    context.getSharedPreferences("pushapp_prefs", Context.MODE_PRIVATE).edit()
                        .putString("fcm_token", token)
                        .apply()

                    try {
                        val responseJson = JSONObject(responseBody ?: "{}")
                        val device = responseJson.optJSONObject("device")
                        persistGuestId(device?.optString("user_id"))
                        PushAppLogger.debug("PushApp", "Guest ID: $guestId")
                    } catch (e: Exception) {
                        PushAppLogger.error("PushApp", "Error parsing sendToken response: ${e.message}")
                    }

                    flushBufferedEvents()
                    notifyAppOpen()
                } else {
                    PushAppLogger.error("PushApp", "SendToken API failed: HTTP ${response.code}")
                    PushAppLogger.error("PushApp", "Response Body: $responseBody")
                }
                response.close()
                callback?.let { cb ->
                    Handler(Looper.getMainLooper()).post { cb(success) }
                }
            }
        })
    }

    // ------------------ Login ------------------

    fun login(userId: String): Boolean {
        if (lifecycleState == SdkLifecycle.NOT_INITIALIZED || serverUrl.isEmpty()) {
            logSequenceError("login() requires initialize() first", lifecycleState)
            return false
        }
        if (lifecycleState == SdkLifecycle.INITIALIZED) {
            logSequenceError("login() requires register() first", lifecycleState)
            return false
        }
        if (userId.isBlank()) {
            PushAppLogger.error("PushApp", "login() failed: userId is empty")
            return false
        }

        this.userId = userId
        lifecycleState = SdkLifecycle.LOGGED_IN
        val url = "$serverUrl/pushapp/api/device/link"

        val json = JSONObject().apply {
            put("user_id", userId)
            put("device_id", getPersistentDeviceId())
            put("channel_id", channelId)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(body)

        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
            PushAppLogger.debug("PushApp", "Header: $k = $v")
        }

        PushAppLogger.debug("PushApp", "Login API URL: $url")
        PushAppLogger.debug("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Login API failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    // Persist userId in SharedPreferences
                    val prefs = context.getSharedPreferences("PushAppPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("pushapp_user_id", userId).apply()
                    PushAppLogger.debug("PushApp", "Login API success, code=${response.code}")
                    PushAppLogger.debug("PushApp", "Response Body: $responseBody")

                    flushBufferedEvents()
                    connectSocket()
                } else {
                    PushAppLogger.error("PushApp", "Login API failed: HTTP ${response.code}")
                    PushAppLogger.error("PushApp", "Response Body: $responseBody")
                }
                response.close()
            }
        })
        return true
    }

    fun logout(callback: (Boolean) -> Unit) {
        if (lifecycleState == SdkLifecycle.NOT_INITIALIZED || serverUrl.isEmpty()) {
            logSequenceError("logout() requires initialize() first", lifecycleState)
            Handler(Looper.getMainLooper()).post { callback(false) }
            return
        }

        val delinkUserId = userId
        clearLocalUserSession()

        if (delinkUserId.isNullOrBlank()) {
            Handler(Looper.getMainLooper()).post { callback(true) }
            return
        }

        val url = "$serverUrl/pushapp/api/device/delink"
        val json = JSONObject().apply {
            put("user_id", delinkUserId)
            put("device_id", getPersistentDeviceId())
            put("channel_id", channelId)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder().url(url).post(body)
        getDeviceHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        httpClient.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Logout delink API failed: ${e.message}")
                Handler(Looper.getMainLooper()).post { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                if (!success) {
                    PushAppLogger.error("PushApp", "Logout delink API failed: HTTP ${response.code}")
                }
                response.close()
                Handler(Looper.getMainLooper()).post { callback(success) }
            }
        })
    }

    private fun clearLocalUserSession() {
        userId = null
        webSocketManager?.disconnect()
        webSocketManager = null
        lifecycleState = if (hasRegistered()) SdkLifecycle.REGISTERED else SdkLifecycle.INITIALIZED
        context.getSharedPreferences("PushAppPrefs", Context.MODE_PRIVATE)
            .edit()
            .remove("pushapp_user_id")
            .apply()
    }

    /**
     * Creates or updates customer profile. Call after login with the same `code` your app uses (e.g. userId_deviceId).
     * Matches Flutter/iOS: PUT to .../customer/profile?code=... with body { additionalInfo, cohorts, code }.
     */
    fun createOrUpdateCustomerProfile(
        code: String,
        additionalInfo: Map<String, Any>,
        cohorts: Map<String, Any>,
        callback: (Boolean) -> Unit
    ) {
        if (serverUrl.isEmpty()) {
            PushAppLogger.error("PushApp", "PushApp not initialized. Call initialize() first.")
            Handler(Looper.getMainLooper()).post { callback(false) }
            return
        }
        if (code.isEmpty()) {
            PushAppLogger.error("PushApp", "code is required for customer profile")
            Handler(Looper.getMainLooper()).post { callback(false) }
            return
        }

        val encodedCode = java.net.URLEncoder.encode(code, "UTF-8")
        val url = "$serverUrl/pushapp/api/v1/customer/profile?code=$encodedCode"

        val bodyJson = JSONObject().apply {
            put("additionalInfo", mapToJsonObject(additionalInfo))
            put("cohorts", mapToJsonObject(cohorts))
            put("code", code)
        }

        val requestBody = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder().url(url).put(requestBody)
            .addHeader("Content-Type", "application/json")

        getDeviceHeaders().forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
        }

        PushAppLogger.debug("PushApp", "Customer profile PUT: $url")
        PushAppLogger.debug("PushApp", "Request Body: ${bodyJson.toString(2)}")

        httpClient.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Customer profile request failed: ${e.message}")
                Handler(Looper.getMainLooper()).post { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                PushAppLogger.debug("PushApp", "Customer profile response: ${response.code}")
                if (!success) {
                    PushAppLogger.error("PushApp", "Response body: ${response.body?.string()}")
                }
                response.close()
                Handler(Looper.getMainLooper()).post { callback(success) }
            }
        })
    }

    // ------------------ Send Event ------------------

    fun sendEvent(eventName: String, eventData: Map<String, Any>) {
        if (serverUrl.isEmpty()) {
            PushAppLogger.error("PushApp", "PushApp not initialized. Call initialize() first.")
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
            PushAppLogger.debug("PushApp", "Header: $k = $v")
        }

        PushAppLogger.debug("PushApp", "Event Triggered: $eventName")
        PushAppLogger.debug("PushApp", "Event Data: $eventData")
        PushAppLogger.debug("PushApp", "Event URL: $url")
        PushAppLogger.debug("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Event send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    PushAppLogger.debug("PushApp", "Event sent successfully")
                    PushAppLogger.debug("PushApp", "Response Body: $bodyStr")
                    Handler(Looper.getMainLooper()).postDelayed({
                        userId?.let { pollForInApp(it) }
                    }, 2000)
                } else {
                    PushAppLogger.error("PushApp", "Failed to send event: ${response.code}")
                    PushAppLogger.error("PushApp", "Response Body: $bodyStr")
                }
                response.close()
            }
        })
    }

    fun updateDeviceToken(token: String) {
        PushAppLogger.debug("PushApp", "🔄 updateDeviceToken() called")

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
                PushAppLogger.debug("PushApp", "Header: $k = $v")
            }

            PushAppLogger.debug("PushApp", "URL: $url")
            PushAppLogger.debug("PushApp", "Request Body: ${json.toString(2)}")

            val request = requestBuilder.build()
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    PushAppLogger.error("PushApp", "🔥 Error updating device token: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful) {
                        PushAppLogger.debug("PushApp", "✅ Token updated successfully on server.")
                        PushAppLogger.debug("PushApp", "Response Body: $bodyStr")
                    } else {
                        PushAppLogger.error("PushApp", "❌ Failed to update token: ${response.code}")
                        PushAppLogger.error("PushApp", "Response Body: $bodyStr")
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            PushAppLogger.error("PushApp", "🔥 Exception in updateDeviceToken: ${e.message}")
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
            PushAppLogger.debug("PushApp", "Header: $k = $v")
        }

        PushAppLogger.debug("PushApp", "Ack Triggered")
        PushAppLogger.debug("PushApp", "Ack URL: $url")
        PushAppLogger.debug("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Ack send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    PushAppLogger.debug("PushApp", "Ack sent successfully")
                    PushAppLogger.debug("PushApp", "Response Body: $bodyStr")
                } else {
                    PushAppLogger.error("PushApp", "Failed to send ack: ${response.code}")
                    PushAppLogger.error("PushApp", "Response Body: $bodyStr")
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
            PushAppLogger.debug("PushApp", "Header: $k = $v")
        }

        PushAppLogger.debug("PushApp", "Track Triggered: $event")
        PushAppLogger.debug("PushApp", "Track URL: $url")
        PushAppLogger.debug("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Track send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    PushAppLogger.debug("PushApp", "Track sent successfully")
                    PushAppLogger.debug("PushApp", "Response Body: $bodyStr")
                } else {
                    PushAppLogger.error("PushApp", "Failed to send track: ${response.code}")
                    PushAppLogger.error("PushApp", "Response Body: $bodyStr")
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
            PushAppLogger.debug("PushApp", "Header: $k = $v")
        }

        PushAppLogger.debug("PushApp", "Notification Track Triggered: $event")
        PushAppLogger.debug("PushApp", "Track URL: $url")
        PushAppLogger.debug("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Notification Track failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful) {
                    PushAppLogger.debug("PushApp", "Notification Track sent successfully")
                    PushAppLogger.debug("PushApp", "Response Body: $bodyStr")
                } else {
                    PushAppLogger.error("PushApp", "Failed to send Notification Track: ${response.code}")
                    PushAppLogger.error("PushApp", "Response Body: $bodyStr")
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
            PushAppLogger.debug("PushApp", "Header: $k = $v")
        }

        PushAppLogger.debug("PushApp", "Polling In-App URL: $url")
        PushAppLogger.debug("PushApp", "Request Body: ${json.toString(2)}")

        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                PushAppLogger.error("PushApp", "Poll in-app failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    PushAppLogger.debug("PushApp", "Poll in-app successful, code=${response.code}")
                    PushAppLogger.debug("PushApp", "Response Body: $responseBody")

                    if (!responseBody.isNullOrEmpty()) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val success = jsonResponse.optBoolean("success", false)
                            PushAppLogger.debug("PushApp", "JSON Body: $jsonResponse")
                            if (success) {
                                val results = jsonResponse.optJSONArray("results")
                                if (results != null && results.length() > 0) {
                                    val overlayItems = mutableListOf<Map<String, Any>>()
                                    val inlineItems = mutableListOf<Triple<String, String, String>>()

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

                                            PushAppLogger.debug("PushApp", "Processing tooltip for target=$target, title=$title")

                                            // Send ACK for the message
                                            ackInApp(messageId)

                                            // Check if there's a registered tooltip target for this target ID
                                            val tooltipTarget = PlaceholderViewManager.getTooltipTarget(target)
                                            PushAppLogger.debug("PushApp", "Looking for tooltip target: $target")
                                            PushAppLogger.debug("PushApp", "Available tooltip targets: ${PlaceholderViewManager.getAllTooltipTargetIds()}")
                                            
                                            if (tooltipTarget != null) {
                                                // Display tooltip at registered target position
                                                PushAppLogger.debug("PushApp", "✅ Found registered tooltip target: $target")
                                                PushAppLogger.debug("PushApp", "Target coordinates: x=${tooltipTarget.x}, y=${tooltipTarget.y}, width=${tooltipTarget.width}, height=${tooltipTarget.height}")
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
                                                PushAppLogger.debug("PushApp", "No registered tooltip target found for: $target, emitting tooltip event")
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

                                        // Inline: route to registered placeholder (after overlays, like iOS)
                                        if (layoutCode.contains("inline", ignoreCase = true) && placeholderId.isNotBlank()) {
                                            PushAppLogger.debug("PushApp", "Queued inline for placeholder: $placeholderId")
                                            inlineItems.add(Triple(placeholderId, messageId, html))
                                            continue
                                        }

                                        overlayItems.add(
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

                                        PushAppLogger.debug("PushApp", "Overlay items queued: ${overlayItems.size}, inline: ${inlineItems.size}")
                                    }

                                    val dispatchInlineItems = {
                                        for ((placeholderId, messageId, html) in inlineItems) {
                                            PushAppLogger.debug("PushApp", "Dispatching to placeholder: $placeholderId")
                                            ackInApp(messageId)
                                            PushAppPlaceholderManager.dispatchPlaceholderContent(
                                                placeholderId,
                                                messageId,
                                                html,
                                            )
                                        }
                                    }

                                    val activity = currentActivityRef?.get()
                                    if (activity == null) {
                                        PushAppLogger.warn("PushApp", "No activity for in-app display")
                                        dispatchInlineItems()
                                    } else if (overlayItems.isNotEmpty()) {
                                        try {
                                            PushAppLogger.debug("PushApp", "Displaying ${overlayItems.size} overlay in-app items")
                                            activity.runOnUiThread {
                                                InAppDisplay(activity).showInApps(overlayItems) {
                                                    dispatchInlineItems()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            PushAppLogger.error("PushApp", "Error showing in-app: ${e.message}")
                                            dispatchInlineItems()
                                        }
                                    } else {
                                        dispatchInlineItems()
                                    }

                                    if (overlayItems.isEmpty() && inlineItems.isEmpty()) {
                                        PushAppLogger.warn("PushApp", "No valid in-app items to display")
                                    }
                                } else {
                                    PushAppLogger.warn("PushApp", "No in-app notification results found")
                                }
                            } else {
                                PushAppLogger.warn("PushApp", "Poll in-app returned success=false")
                            }
                        } catch (e: Exception) {
                            PushAppLogger.error("PushApp", "Error parsing poll in-app response: ${e.message}")
                        }
                    } else {
                        PushAppLogger.warn("PushApp", "Empty poll in-app response body")
                    }
                } else {
                    PushAppLogger.error("PushApp", "Poll in-app failed: HTTP ${response.code}")
                    PushAppLogger.error("PushApp", "Response Body: $responseBody")
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
        PushAppLogger.debug("PushApp", "Buffered event: $eventName")
    }

    private fun flushBufferedEvents() {
        val userIdToUse = userId ?: guestId ?: return
        val sharedPrefs = context.getSharedPreferences("pushapp", Context.MODE_PRIVATE)
        val bufferJsonArray = JSONArray(sharedPrefs.getString("event_buffer", "[]"))
        if (bufferJsonArray.length() == 0) return

        PushAppLogger.debug("PushApp", "Flushing ${bufferJsonArray.length()} buffered events")
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
            PushAppLogger.debug("PushApp", "No userId or guestId available to connect socket")
            return
        }

        webSocketManager?.disconnect()
        webSocketManager = WebSocketManager(id, tenant, sandbox) { data ->
            PushAppLogger.debug("PushApp", "WebSocket received data: $data")
            handleSocketMessage(data)
        }
        webSocketManager?.connect()
    }

    fun handleNotification(data: Map<String, String>) {
        PushAppLogger.debug("PushApp", "Handling notification data: $data")

        val type = data["type"]?.lowercase()
        if (type == "in_app") {
            val activity = currentActivityRef?.get()
            if (activity != null) {
                handleSocketNotification(data)
                return
            }
            PushAppLogger.warn(
                "PushApp",
                "in_app FCM received with no foreground activity — showing system notification fallback",
            )
        }

        PushNotificationDisplay.displayFromData(context, data)
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
            PushAppLogger.warn("PushApp", "No active activity available to show in-app notification")
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
                            PushAppLogger.error("PushApp", "Error showing in-app notification: ${e.message}")
                        }
                    }
                } else {
                    PushAppLogger.warn("PushApp", "No valid in-app items found in socket notification")
                }

            } catch (e: Exception) {
                PushAppLogger.error("PushApp", "Failed to parse in-app data: ${e.message}")
            }
        } else {
            PushAppLogger.debug("PushApp", "Notification is not in-app or data is missing")
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

