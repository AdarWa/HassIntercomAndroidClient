package net.adarw.hassintercom.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import java.nio.charset.StandardCharsets
import net.adarw.hassintercom.Client
import net.adarw.hassintercom.START_AUDIO_COMMAND
import net.adarw.hassintercom.STOP_AUDIO_COMMAND
import net.adarw.hassintercom.protocol.AudioFormat
import net.adarw.hassintercom.utils.StreamPrefs
import net.adarw.hassintercom.utils.buildNotification
import net.adarw.hassintercom.utils.showErrorNotification

class AudioMqttService : Service() {

  private lateinit var streamPrefs: StreamPrefs
  private lateinit var mqtt: Mqtt5AsyncClient
  private var mqttConnected = false
  private var isStreaming = false

  override fun onCreate() {
    super.onCreate()
    Log.i(TAG, "AudioMQTTService created")
    streamPrefs = StreamPrefs(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand: intent=$intent flags=$flags startId=$startId")
    startForeground(1, buildNotification(this, isStreaming))
    connectMqtt()
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun connectMqtt() {
    Log.i(TAG, "Connecting to MQTT at ${streamPrefs.mqttURI} with clientId=${streamPrefs.clientId}")
    mqtt =
        MqttClient.builder()
            .identifier(streamPrefs.clientId)
            .serverHost(streamPrefs.mqttHost)
            .serverPort(streamPrefs.mqttPort.toIntOrNull() ?: 1883)
            .useMqttVersion5()
            .buildAsync()
    mqtt
        .connectWith()
        .simpleAuth()
        .username(streamPrefs.mqttUser)
        .password(streamPrefs.mqttPassword.toByteArray())
        .applySimpleAuth()
        .send()
        .whenComplete { ack, e ->
          if (e != null) {
            Log.e(TAG, "Failed to connect to MQTT", e)
            mqttConnected = false
            startForeground(1, buildNotification(this, false))
            showErrorNotification(e.message ?: "Unknown exception connecting to MQTT")
            return@whenComplete
          }
          subscribeToTopic()
          mqttConnected = true
          Log.i(TAG, "MQTT connection complete. ack=$ack")
        }
  }

  private fun subscribeToTopic() {
    Log.d(TAG, "Subscribing to MQTT topic ${streamPrefs.mqttTopic}")
    try {
      mqtt
          .subscribeWith()
          .topicFilter(streamPrefs.mqttTopic)
          .callback { payload ->
              acceptPayload(
                  StandardCharsets.UTF_8.decode(payload.topic.toByteBuffer()).toString(),
                  StandardCharsets.UTF_8.decode(payload.payload.get()).toString())
          }
          .send()
    } catch (ex: Exception) {
      ex.printStackTrace()
      Log.e(TAG, "Exception subscribing to MQTT topic ${streamPrefs.mqttTopic}", ex)
      showErrorNotification(ex.message ?: "Unknown error while subscribing to MQTT topic")
    }
  }

  private fun acceptPayload(topic: String, payload: String) {
    Log.d(TAG, "Handling payload topic=$topic payload=$payload")
    if (topic == streamPrefs.mqttTopic) {
      if (payload == START_AUDIO_COMMAND) {
        Log.i(TAG, "Start audio command received")
        startAudio()
      } else if (payload == STOP_AUDIO_COMMAND) {
        Log.i(TAG, "Stop audio command received")
        stopAudio()
      } else {
        val message = "Unsupported command: $payload"
        Log.w(TAG, message)
        showErrorNotification(message)
      }
    }
  }

  private fun startAudio() {
    Log.i(TAG, "Starting audio client")
    try {
      Client.startClient(
          streamPrefs.host,
          streamPrefs.port.toIntOrNull() ?: 8765,
          streamPrefs.clientId,
          AudioFormat(
              sampleRate = streamPrefs.sampleRate.toIntOrNull() ?: 16000,
              frameMs = streamPrefs.frameMs.toIntOrNull() ?: 400),
          this)
      isStreaming = true
      startForeground(1, buildNotification(this, true))
    } catch (e: Exception) {
      e.printStackTrace()
      Log.e(TAG, "Error starting audio client", e)
      showErrorNotification(e.message ?: "Unknown error starting client")
    }
  }

  private fun stopAudio() {
    Log.i(TAG, "Stopping audio client")
    try {
      Client.stopClient()
      isStreaming = false
      startForeground(1, buildNotification(this, true))
    } catch (e: Exception) {
      e.printStackTrace()
      Log.e(TAG, "Error stopping audio client", e)
      showErrorNotification(e.message ?: "Unknown error stopping client")
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "Service destroyed; stopping audio")
    stopAudio()
  }

  companion object {
    private val TAG = AudioMqttService::class.simpleName
  }
}
