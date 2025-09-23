package net.adarw.hassintercom.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import net.adarw.hassintercom.utils.NotificationHelper

class IntercomService : Service() {

  override fun onCreate() {
    super.onCreate()
    startForegroundService()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Start recording and playback
    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startForegroundService() {
    val notification = NotificationHelper.buildNotification(this)
    startForeground(1, notification)
  }
}
