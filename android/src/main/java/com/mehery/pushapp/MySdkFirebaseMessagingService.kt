package com.mehery.pushapp

import android.app.*
import android.content.*
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.ExecutionException

class MySdkFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("MySdk", "Notification received: ${remoteMessage.data}")

        PushApp.getInstance().lastNotificationData = remoteMessage.data
        PushApp.getInstance().handleNotification(remoteMessage.data)

        val data = remoteMessage.data

        val isLiveActivity = data.containsKey("message1") &&
                data.containsKey("message2") &&
                data.containsKey("message3")

        if (isLiveActivity) {
            handleLiveActivityNotification(data)
        } else {
            val title = data["title"]
            val body = data["body"]
            if (!title.isNullOrBlank() && !body.isNullOrBlank()) {
                sendStandardNotification(title, body, data)
            }
        }
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("MySdk", "New token received: $token")
        PushApp.getInstance().handleDeviceToken(token)
    }

    private fun sendStandardNotification(
        title: String,
        messageBody: String,
        data: Map<String, String>
    ) {
        val channelId = "pushapp_channel_id"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PushApp Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = (data["activity_id"] ?: "activity_${System.currentTimeMillis()}").hashCode()
        val click_token = data["click_token"]
        // Intent for main notification click
        val clickIntent = Intent(this, NotificationClickReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
            putExtra("click_token", data["click_token"])
            data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        // Add optional image
        data["image"]?.let { imageUrl ->
            try {
                val future = Glide.with(this).asBitmap().load(imageUrl).submit()
                val bitmap = future.get()
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            } catch (e: ExecutionException) {
                Log.e("MySdk", "Image load failed: ${e.message}")
            }
        }

        // Action buttons using BroadcastReceiver instead of ACTION_VIEW
        addActionButton(builder, data["title1"],data["action1"], data["url1"], 101,click_token,notificationId)
        addActionButton(builder, data["title2"],data["action2"], data["url2"], 102,click_token,notificationId)

        notificationManager.notify(notificationId, builder.build())
    }



    private fun addActionButton(
        builder: NotificationCompat.Builder,
        buttonTitle: String?,
        action: String?,
        url: String?,
        requestCode: Int,
        clickToken: String?,
        notificationId: Int
    ) {
        if (!buttonTitle.isNullOrBlank() && !url.isNullOrBlank() && !clickToken.isNullOrBlank() && !action.isNullOrBlank()) {
            val intent = Intent(this, NotificationClickReceiver::class.java).apply {
                putExtra("notification_id", notificationId)
                putExtra("click_token", clickToken)
                putExtra("action_id", action) // CTA identifier
                putExtra("url", url)          // URL to open
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder.addAction(0, buttonTitle, pendingIntent)
        }
    }



    private fun handleLiveActivityNotification(data: Map<String, String>) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // ✅ Create the "live_activity_channel" if not already created
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "live_activity_channel"
                val channelName = "Live Activity Notifications"
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }

            val customService = CustomNotificationService(this)

            val notificationId = (data["activity_id"] ?: "activity_${System.currentTimeMillis()}").hashCode()

            val notification = customService.createCustomNotification(
                channelId = "live_activity_channel",
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
                notificationId = notificationId
            )
            println("NotifID 2")
            println(notificationId)
            notificationManager.notify(notificationId, notification.build())

        } catch (e: Exception) {
            Log.e("MySdk", "Live activity error: ${e.message}", e)
        }
    }

}


class NotificationClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", -1)
        val data = intent.extras?.let { bundle ->
            bundle.keySet().associateWith { bundle[it].toString() }
        } ?: emptyMap()

        Log.d("MySdk", "Notification clicked! ID: $notificationId, data: $data")

        val clickToken = data["click_token"]
        val ctaId = data["action_id"]
        val url = data["url"]

        if (!clickToken.isNullOrEmpty()) {
            PushApp.getInstance().trackNotificationEvent(
                clickToken = clickToken,
                event = if (ctaId.isNullOrEmpty()) "opened" else "cta",
                ctaId = ctaId
            )
        }

        // Open the URL if CTA clicked
        url?.let {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e("PushApp", "Failed to open URL: $it", e)
            }
        }

        // Cancel the notification
        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }
}



