package net.adarw.hassintercom.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {

  fun buildNotification(context: Context): Notification {
    val channelId = "intercom_service"
    val channel =
        NotificationChannel(channelId, "Intercom Service", NotificationManager.IMPORTANCE_LOW)
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)

    return Notification.Builder(context, channelId)
        .setContentTitle("Intercom Active")
        .setContentText("Streaming audio...")
        .build()
  }
}
