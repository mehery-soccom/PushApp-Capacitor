package com.mehery.pushapp

import android.app.PendingIntent
import android.content.Context
import android.graphics.*
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.os.Handler
import android.os.Looper
import android.app.NotificationManager
import android.graphics.drawable.GradientDrawable
import com.pushapp.ionic.R


class CustomNotificationService(private val context: Context) {

    fun createCustomNotification(
        channelId: String,
        title: String,
        message: String,
        tapText: String,
        progress: Int,
        titleColor: String,
        messageColor: String,
        tapTextColor: String,
        progressColor: String,
        backgroundColor: String,
        imageUrl: String,
        bg_color_gradient: String,
        bg_color_gradient_dir: String,
        align: String,
        notificationId: Int
    ): NotificationCompat.Builder {
        val customView = RemoteViews(context.packageName, R.layout.custom_notification_layout)

        // 🔵 Apply background (solid or gradient)
        try {
            if (bg_color_gradient.isNotEmpty() && bg_color_gradient_dir.isNotEmpty()) {
                val startColor = Color.parseColor(backgroundColor)
                val endColor = Color.parseColor(bg_color_gradient)
                val isHorizontal = bg_color_gradient_dir.equals("horizontal", ignoreCase = true)

                val gradientBitmap = createGradientBitmap(startColor, endColor, isHorizontal)
                customView.setImageViewBitmap(R.id.root_background, gradientBitmap)
            } else {
                val solidBitmap = createSolidColorBitmap(Color.parseColor(backgroundColor))
                customView.setImageViewBitmap(R.id.root_background, solidBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        customView.setTextViewText(R.id.title, title)
        customView.setTextViewText(R.id.message, message)
        customView.setTextViewText(R.id.tap_text, tapText)

        try {
            customView.setTextColor(R.id.title, Color.parseColor(titleColor))
            customView.setTextColor(R.id.message, Color.parseColor(messageColor))
            customView.setTextColor(R.id.tap_text, Color.parseColor(tapTextColor))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        customView.setProgressBar(R.id.progress_bar, 100, progress, false)
        customView.setImageViewResource(R.id.icon, R.mipmap.ic_launcher)

        // NOTE: Removed setLayoutDirection calls here because RemoteViews does not support them.
        // Handle layoutDirection in your XML layouts with android:layoutDirection attribute if needed.

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText(tapText)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent)

        if (imageUrl.isNotEmpty()) {
            downloadImage(imageUrl) { bitmap ->
                if (bitmap != null) {
                    val updatedView = RemoteViews(context.packageName, R.layout.custom_notification_layout)

                    try {
                        val bgBitmap = if (bg_color_gradient.isNotEmpty() && bg_color_gradient_dir.isNotEmpty()) {
                            val startColor = Color.parseColor(backgroundColor)
                            val endColor = Color.parseColor(bg_color_gradient)
                            val isHorizontal = bg_color_gradient_dir.equals("horizontal", ignoreCase = true)
                            createGradientBitmap(startColor, endColor, isHorizontal)
                        } else {
                            createSolidColorBitmap(Color.parseColor(backgroundColor))
                        }
                        updatedView.setImageViewBitmap(R.id.root_background, bgBitmap)
                    } catch (_: Exception) {}

                    updatedView.setTextViewText(R.id.title, title)
                    updatedView.setTextViewText(R.id.message, message)
                    updatedView.setTextViewText(R.id.tap_text, tapText)
                    updatedView.setTextColor(R.id.title, Color.parseColor(titleColor))
                    updatedView.setTextColor(R.id.message, Color.parseColor(messageColor))
                    updatedView.setTextColor(R.id.tap_text, Color.parseColor(tapTextColor))
                    updatedView.setProgressBar(R.id.progress_bar, 100, progress, false)
                    updatedView.setImageViewBitmap(R.id.icon, bitmap)

                    // Removed setLayoutDirection here as well.

                    val updatedBuilder = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setCustomContentView(updatedView)
                        .setCustomBigContentView(updatedView)
                        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setSubText(tapText)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(pendingIntent)
                    println("NotifID 3")
                    println(notificationId)
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(notificationId, updatedBuilder.build())
                }
            }
        }

        return builder
    }

    fun createGradientBitmap(
        startColor: Int,
        endColor: Int,
        isHorizontal: Boolean,
        width: Int = 1080,
        height: Int = 300
    ): Bitmap {
        val orientation = if (isHorizontal) GradientDrawable.Orientation.LEFT_RIGHT
        else GradientDrawable.Orientation.TOP_BOTTOM

        val gradient = GradientDrawable(orientation, intArrayOf(startColor, endColor))
        gradient.setBounds(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        gradient.draw(canvas)
        return bitmap
    }

    fun createSolidColorBitmap(color: Int, width: Int = 1080, height: Int = 300): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    fun downloadImage(urlString: String, onResult: (Bitmap?) -> Unit) {
        val cleanUrl = if (urlString.startsWith("@")) urlString.substring(1) else urlString
        Thread {
            var bitmap: Bitmap? = null
            try {
                val url = URL(cleanUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()
                if (connection.responseCode == 200) {
                    val input: InputStream = connection.inputStream
                    bitmap = BitmapFactory.decodeStream(input)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Handler(Looper.getMainLooper()).post {
                onResult(bitmap)
            }
        }.start()
    }

    fun testImageLoading(imageUrl: String, callback: (Boolean) -> Unit) {
        downloadImage(imageUrl) { bitmap ->
            callback(bitmap != null)
        }
    }
}
