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
              Log.i(TAG, "MQTT connection complete. reconnect=$reconnect serverURI=$serverURI")
            }

            override fun connectionLost(cause: Throwable?) {
              mqttConnected = false
              if (cause != null) {
                Log.w(TAG, "MQTT connection lost", cause)
              } else {
                Log.w(TAG, "MQTT connection lost without cause")
              }
            }

            @Throws(java.lang.Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
              Log.d(TAG, "MQTT message arrived: topic=$topic payload=${String(message.payload)}")
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
              Log.i(TAG, "MQTT connected successfully")
              subscribeToTopic()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
              Log.e(TAG, "MQTT connection failed", exception)
              throw RuntimeException(exception.message ?: "Unknown MQTT failure")
            }
          })
    } catch (e: Exception) {
      Log.e(TAG, "Error connecting to MQTT", e)
      showErrorNotification(e.message ?: "Unknown error while connecting to MQTT")
    }
  }

  private fun subscribeToTopic() {
    Log.d(TAG, "Subscribing to MQTT topic ${streamPrefs.mqttTopic}")
    try {
      mqtt.subscribe(
          streamPrefs.mqttTopic,
          0,
          null,
          object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
              mqttConnected = true
              Log.i(TAG, "Subscribed to MQTT topic ${streamPrefs.mqttTopic}")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
              mqttConnected = false
              if (exception != null) {
                Log.e(TAG, "Failed to subscribe to MQTT topic ${streamPrefs.mqttTopic}", exception)
              } else {
                Log.e(TAG, "Failed to subscribe to MQTT topic ${streamPrefs.mqttTopic}")
              }
            }
          })
    } catch (ex: MqttException) {
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
        Log.w(TAG, "Unsupported command received: $payload")
        throw RuntimeException("Unsupported command: $payload")
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
      startForeground(1, buildNotification(this, isStreaming))
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
      startForeground(1, buildNotification(this, isStreaming))
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
