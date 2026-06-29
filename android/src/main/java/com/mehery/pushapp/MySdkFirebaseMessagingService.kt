package com.mehery.pushapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.NotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MySdkFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        PushAppLogger.debug(
            "MySdk",
            "FCM message received (dataKeys=${remoteMessage.data.keys}, hasNotification=${remoteMessage.notification != null})",
        )

        val data = PushNotificationDisplay.normalizePushData(
            remoteMessage.data,
            remoteMessage.notification,
        )

        val pushApp = PushApp.getInstance()
        pushApp.lastNotificationData = data

        if (!pushApp.isInitialized()) {
            PushAppLogger.warn("MySdk", "SDK not initialized — posting system notification directly")
            PushNotificationDisplay.displayFromData(applicationContext, data)
            return
        }

        pushApp.handleNotification(data)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushAppLogger.logPushToken("MySdk", "FCM token (onNewToken)", token)
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

        PushAppLogger.debug("PushApp", "Notification clicked, ID = $notificationId")

        val clickToken = data["click_token"]
        if (clickToken.isNullOrEmpty()) {
            PushAppLogger.error("PushApp", "Missing click_token in payload")
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
        PushAppLogger.debug("PushApp", "Push track ($event) sent.")

        if (!url.isNullOrEmpty()) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                PushAppLogger.error("PushApp", "Failed to open notification URL", e)
            }
        }

        if (notificationId != -1) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
        }
    }
}



