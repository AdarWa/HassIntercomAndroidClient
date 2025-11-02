package net.adarw.hassintercom.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import net.adarw.hassintercom.Client
import net.adarw.hassintercom.utils.StreamPrefs
import net.adarw.hassintercom.protocol.AudioFormat
import net.adarw.hassintercom.utils.buildNotification
import net.adarw.hassintercom.utils.showErrorNotification

class AudioMqttService : Service() {

  private lateinit var streamPrefs: StreamPrefs

  override fun onCreate() {
    super.onCreate()
    streamPrefs = StreamPrefs(this)
    buildNotification(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    connectMqtt()
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun connectMqtt() {
    TODO()
  }

  private fun startAudio() {
    try {
      Client.startClient(
          streamPrefs.host,
          streamPrefs.port.toIntOrNull() ?: 8765,
          streamPrefs.clientId,
          AudioFormat(
              sampleRate = streamPrefs.sampleRate.toIntOrNull() ?: 16000,
              frameMs = streamPrefs.frameMs.toIntOrNull() ?: 400),
          this)
    } catch (e: Exception) {
      e.printStackTrace()
      showErrorNotification(e.message ?: "Unknown error starting client", this)
    }
  }

  private fun stopAudio() {
    try {
      Client.stopClient()
    } catch (e: Exception) {
      e.printStackTrace()
      showErrorNotification(e.message ?: "Unknown error stopping client", this)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    stopAudio()
  }
}
