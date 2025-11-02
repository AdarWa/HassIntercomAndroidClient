package net.adarw.hassintercom.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import net.adarw.hassintercom.Client
import net.adarw.hassintercom.START_AUDIO_COMMAND
import net.adarw.hassintercom.STOP_AUDIO_COMMAND
import net.adarw.hassintercom.protocol.AudioFormat
import net.adarw.hassintercom.utils.StreamPrefs
import net.adarw.hassintercom.utils.buildNotification
import net.adarw.hassintercom.utils.showErrorNotification
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

class AudioMqttService : Service() {

  private lateinit var streamPrefs: StreamPrefs
  private lateinit var mqtt: MqttAndroidClient
  private var mqttConnected = false


  override fun onCreate() {
    super.onCreate()
    Log.i(TAG,"Started AudioMQTTService")
    streamPrefs = StreamPrefs(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(1, buildNotification(this))
    connectMqtt()
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun connectMqtt() {
    mqtt = MqttAndroidClient(this, streamPrefs.mqttURI, streamPrefs.clientId)
    val options =
        MqttConnectOptions().apply {
          isAutomaticReconnect = true
          isCleanSession = false
          if (streamPrefs.mqttUser != "" && streamPrefs.mqttPassword != "") {
            userName = streamPrefs.mqttUser
            password = streamPrefs.mqttPassword.toCharArray()
          }
        }
    try {
      mqtt.setCallback(
          object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
              if (reconnect) subscribeToTopic()
              mqttConnected = true
            }

            override fun connectionLost(cause: Throwable?) {
              mqttConnected = false
            }

            @Throws(java.lang.Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
              acceptPayload(topic, String(message.payload))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
          })
      mqtt.connect(
          options,
          object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
              val disconnectedBufferOptions =
                  DisconnectedBufferOptions().apply {
                    isBufferEnabled = true
                    bufferSize = 100
                    isPersistBuffer = false
                    isDeleteOldestMessages = false
                  }
              mqtt.setBufferOpts(disconnectedBufferOptions)
              subscribeToTopic()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
              throw RuntimeException(exception.message ?: "Unknown MQTT failure")
            }
          })
    } catch (e: Exception) {
      showErrorNotification(e.message ?: "Unknown error while connecting to MQTT")
    }
  }

  private fun subscribeToTopic() {
    try {
      mqtt.subscribe(
          streamPrefs.mqttTopic,
          0,
          null,
          object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
              mqttConnected = true
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
              mqttConnected = false
            }
          })
    } catch (ex: MqttException) {
      ex.printStackTrace()
      showErrorNotification(ex.message ?: "Unknown error while subscribing to MQTT topic")
    }
  }

  private fun acceptPayload(topic: String, payload: String) {
    if (topic == streamPrefs.mqttTopic) {
      if (payload == START_AUDIO_COMMAND) {
        startAudio()
      } else if (payload == STOP_AUDIO_COMMAND) {
        stopAudio()
      } else {
        throw RuntimeException("Unsupported command: $payload")
      }
    }
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
      showErrorNotification(e.message ?: "Unknown error starting client")
    }
  }

  private fun stopAudio() {
    try {
      Client.stopClient()
    } catch (e: Exception) {
      e.printStackTrace()
      showErrorNotification(e.message ?: "Unknown error stopping client")
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    stopAudio()
  }

  companion object {
    private val TAG = AudioMqttService::class.simpleName
  }
}
