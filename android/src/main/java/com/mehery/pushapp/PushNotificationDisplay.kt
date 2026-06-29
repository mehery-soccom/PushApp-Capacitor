package com.mehery.pushapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.ExecutionException

/**
 * Builds and posts system notifications from FCM data payloads when the
 * FCM `notification` block is absent or incomplete.
 */
object PushNotificationDisplay {
    private const val TAG = "PushApp"
    private const val CHANNEL_ID = "pushapp_channel_id"
    private const val LIVE_ACTIVITY_CHANNEL_ID = "live_activity_channel"

    fun normalizePushData(
        data: Map<String, String>,
        notification: RemoteMessage.Notification?,
    ): Map<String, String> {
        val merged = data.toMutableMap()
        notification?.title?.takeIf { it.isNotBlank() && merged["title"].isNullOrBlank() }?.let {
            merged["title"] = it
        }
        notification?.body?.takeIf { it.isNotBlank() && merged["body"].isNullOrBlank() }?.let {
            merged["body"] = it
        }
        notification?.imageUrl?.toString()
            ?.takeIf { it.isNotBlank() && merged["imageUrl"].isNullOrBlank() && merged["image"].isNullOrBlank() }
            ?.let { merged["imageUrl"] = it }
        return merged
    }

    fun displayFromData(context: Context, data: Map<String, String>) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !notificationManager.areNotificationsEnabled()) {
            Log.e(
                TAG,
                "Notifications are disabled — on Android 13+ grant POST_NOTIFICATIONS when the app prompts, " +
                    "or enable notifications in system settings",
            )
            return
        }

        if (isLiveActivity(data)) {
            displayLiveActivity(context, data)
            return
        }

        val title = extractTitle(data)
        val body = extractBody(data)
        if (title.isNullOrBlank() && body.isNullOrBlank()) {
            Log.w(TAG, "Push data has no displayable title/body — skipping system notification")
            return
        }

        displayStandard(
            context = context,
            title = title?.takeIf { it.isNotBlank() } ?: "Notification",
            body = body.orEmpty(),
            data = data,
        )
    }

    private fun isLiveActivity(data: Map<String, String>): Boolean =
        data.containsKey("message1") && data.containsKey("message2") && data.containsKey("message3")

    private fun extractTitle(data: Map<String, String>): String? =
        data["title"]?.takeIf { it.isNotBlank() }
            ?: data["notification_title"]?.takeIf { it.isNotBlank() }
            ?: data["gcm.notification.title"]?.takeIf { it.isNotBlank() }

    private fun extractBody(data: Map<String, String>): String? =
        data["body"]?.takeIf { it.isNotBlank() }
            ?: data["message"]?.takeIf { it.isNotBlank() }
            ?: data["text"]?.takeIf { it.isNotBlank() }
            ?: data["notification_body"]?.takeIf { it.isNotBlank() }
            ?: data["gcm.notification.body"]?.takeIf { it.isNotBlank() }

    private fun extractImageUrl(data: Map<String, String>): String? =
        data["imageUrl"]?.takeIf { it.isNotBlank() }
            ?: data["image"]?.takeIf { it.isNotBlank() }

    private fun notificationId(data: Map<String, String>): Int =
        (data["id"] ?: data["activity_id"] ?: "push_${System.currentTimeMillis()}").hashCode()

    private fun smallIcon(context: Context): Int {
        val appIcon = context.applicationInfo.icon
        return if (appIcon != 0) appIcon else android.R.drawable.ic_dialog_info
    }

    private fun displayStandard(
        context: Context,
        title: String,
        body: String,
        data: Map<String, String>,
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PushApp Notifications",
                NotificationManager.IMPORTANCE_HIGH,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = notificationId(data)
        val clickToken = data["click_token"]

        val clickIntent = Intent(context, NotificationClickReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
            putExtra("click_token", clickToken)
            data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        if (body.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        extractImageUrl(data)?.let { imageUrl ->
            try {
                val future = Glide.with(context).asBitmap().load(imageUrl).submit()
                val bitmap = future.get()
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap).setSummaryText(body))
            } catch (e: ExecutionException) {
                Log.e(TAG, "Notification image load failed: ${e.message}")
            } catch (e: InterruptedException) {
                Log.e(TAG, "Notification image load interrupted: ${e.message}")
                Thread.currentThread().interrupt()
            }
        }

        addActionButton(builder, context, data["title1"], data["action1"], data["url1"], 101, clickToken, notificationId)
        addActionButton(builder, context, data["title2"], data["action2"], data["url2"], 102, clickToken, notificationId)

        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "System notification posted from data payload (id=$notificationId, title=$title)")
    }

    private fun addActionButton(
        builder: NotificationCompat.Builder,
        context: Context,
        buttonTitle: String?,
        action: String?,
        url: String?,
        requestCode: Int,
        clickToken: String?,
        notificationId: Int,
    ) {
        if (
            buttonTitle.isNullOrBlank() || url.isNullOrBlank() ||
            clickToken.isNullOrBlank() || action.isNullOrBlank()
        ) {
            return
        }

        val intent = Intent(context, NotificationClickReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
            putExtra("click_token", clickToken)
            putExtra("action_id", action)
            putExtra("url", url)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        builder.addAction(0, buttonTitle, pendingIntent)
    }

    private fun displayLiveActivity(context: Context, data: Map<String, String>) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    LIVE_ACTIVITY_CHANNEL_ID,
                    "Live Activity Notifications",
                    NotificationManager.IMPORTANCE_HIGH,
                )
                notificationManager.createNotificationChannel(channel)
            }

            val customService = CustomNotificationService(context)
            val notificationId = notificationId(data)

            val notification = customService.createCustomNotification(
                channelId = LIVE_ACTIVITY_CHANNEL_ID,
                title = data["message1"] ?: "",
                message = data["message2"] ?: "",
                tapText = data["message3"] ?: "",
                progress = ((data["progressPercent"]?.toDoubleOrNull() ?: 0.0) * 100).toInt(),
                titleColor = data["message1FontColorHex"] ?: "#FF0000",
                messageColor = data["message2FontColorHex"] ?: "#000000",
                tapTextColor = data["message3FontColorHex"] ?: "#CCCCCC",
                progressColor = data["progressColorHex"] ?: "#00FF00",
                backgroundColor = data["backgroundColorHex"] ?: "#FFFFFF",
                imageUrl = data["imageUrl"] ?: "",
                bg_color_gradient = data["bg_color_gradient"] ?: "",
                bg_color_gradient_dir = data["bg_color_gradient_dir"] ?: "",
                align = data["align"] ?: "",
                notificationId = notificationId,
            )

            notificationManager.notify(notificationId, notification.build())
            Log.d(TAG, "Live activity notification posted (id=$notificationId)")
        } catch (e: Exception) {
            Log.e(TAG, "Live activity notification error: ${e.message}", e)
        }
    }
}
