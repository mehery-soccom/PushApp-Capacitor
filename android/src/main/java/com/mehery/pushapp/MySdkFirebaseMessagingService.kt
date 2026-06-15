package com.mehery.pushapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.NotificationManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MySdkFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("MySdk", "Notification received: ${remoteMessage.data}")

        val data = PushNotificationDisplay.normalizePushData(
            remoteMessage.data,
            remoteMessage.notification,
        )

        PushApp.getInstance().lastNotificationData = data
        PushApp.getInstance().handleNotification(data)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("MySdk", "New token received: $token")
        PushApp.getInstance().handleDeviceToken(token)
    }
}


/**
 * Handles notification body tap and CTA button taps (same behavior as iOS AppDelegate didReceive).
 * Tracks "opened" or "cta" via PushApp.trackNotificationEvent and opens CTA URL when present.
 */
class NotificationClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", -1)
        val data = intent.extras?.let { bundle ->
            bundle.keySet().associateWith { bundle[it].toString() }
        } ?: emptyMap()

        Log.d("PushApp", "📩 Notification clicked, ID = $notificationId")
        Log.d("PushApp", "🧾 userInfo: $data")

        val clickToken = data["click_token"]
        if (clickToken.isNullOrEmpty()) {
            Log.e("PushApp", "❌ Missing click_token in payload")
            if (notificationId != -1) {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
            }
            return
        }

        val ctaId = data["action_id"]
        val event = if (ctaId.isNullOrEmpty()) "opened" else "cta"
        val url = data["url"]?.takeIf { it.isNotBlank() }
            ?: data["notification_url"]?.takeIf { it.isNotBlank() }

        // Track the event (opened or CTA) — same as iOS trackPushNotificationEvent
        PushApp.getInstance().trackNotificationEvent(
            clickToken = clickToken,
            event = event,
            ctaId = if (ctaId.isNullOrEmpty()) null else ctaId
        )
        Log.d("PushApp", "✅ Push track ($event) sent.")

        if (!url.isNullOrEmpty()) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e("PushApp", "❌ Failed to open URL: $url", e)
            }
        }

        if (notificationId != -1) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
        }
    }
}



