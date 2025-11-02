package net.adarw.hassintercom.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import androidx.core.app.NotificationCompat

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

fun showErrorNotification(message: String, ctx: Context) {
  val channelId = "stream_error_channel"

  val channel = NotificationChannel(channelId, "Stream Errors", NotificationManager.IMPORTANCE_HIGH)
  ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

  val notification =
      NotificationCompat.Builder(ctx, channelId)
          .setContentTitle("Stream Client Error")
          .setContentText(message)
          .setSmallIcon(android.R.drawable.stat_notify_error)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .build()

  if (ctx is Service) ctx.startForeground(2, notification)
}
