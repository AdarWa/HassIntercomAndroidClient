package net.adarw.hassintercom.utils

//noinspection SuspiciousImport
import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat

fun buildNotification(context: Context, isStreaming: Boolean): Notification {
  val channelId = "intercom_service"
  val channelName = "Intercom Service"
  val manager = context.getSystemService(NotificationManager::class.java)!!

  val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
  manager.createNotificationChannel(channel)

  // Make sure R.drawable.ic_notification exists and is a valid small icon
  val statusText = if (isStreaming) "Streaming audio..." else "Standing by"

  val builder =
      NotificationCompat.Builder(context, channelId)
          .setContentTitle("Intercom Active")
          .setContentText(statusText)
          .setSmallIcon(R.drawable.ic_notification_overlay) // required on many devices
          .setOngoing(true)

  return builder.build()
}

fun Context.showErrorNotification(message: String) {
  Log.e(this::class.simpleName, message)
  val channelId = "stream_error_channel"

  val channel = NotificationChannel(channelId, "Stream Errors", NotificationManager.IMPORTANCE_HIGH)
  getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

  val notification =
      NotificationCompat.Builder(this, channelId)
          .setContentTitle("Stream Client Error")
          .setContentText(message)
          .setSmallIcon(android.R.drawable.stat_notify_error)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .build()

  if (this is Service) startForeground(2, notification)
}
