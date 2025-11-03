package net.adarw.hassintercom

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.adarw.hassintercom.protocol.AudioFormat
import net.adarw.hassintercom.protocol.AudioSink
import net.adarw.hassintercom.protocol.AudioSource
import net.adarw.hassintercom.protocol.HomeAssistantClient
import net.adarw.hassintercom.service.MicrophoneAudioSource
import net.adarw.hassintercom.service.SimpleAudioSink

object Client {
  private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var haClient: HomeAssistantClient? = null
  var streamId = UUID.randomUUID().toString()

  private val sourceFactory: (AudioFormat) -> AudioSource = { MicrophoneAudioSource(it) }
  private val sinkFactory: (AudioFormat) -> AudioSink = { SimpleAudioSink(it) }

  fun startClient(
      host: String,
      port: Int,
      clientId: String,
      audioFormat: AudioFormat,
      ctx: Context
  ) {
    if (haClient != null) return
    try {
      val audioManager = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
      audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
      audioManager.isSpeakerphoneOn = true
      val client =
          HomeAssistantClient(
              host = host,
              port = port,
              clientId = clientId,
              preferredFormat = audioFormat,
              sourceFactory = sourceFactory,
              sinkFactory = sinkFactory,
              autoStart = true,
              requestedStreamId = streamId)
      haClient = client

      clientScope.launch {
        try {
          client.run()
        } catch (e: CancellationException) {
          // coroutine was cancelled, ignore
        } catch (e: Exception) {
          e.printStackTrace()
          throw RuntimeException(e.message ?: "Unknown error in client.run()")
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      throw RuntimeException(e.message ?: "Failed to start client")
    }
  }

  fun stopClient() {
    clientScope.launch {
      try {
        haClient?.requestStopAudio(streamId)
      } catch (e: Exception) {
        e.printStackTrace()
        throw RuntimeException(e.message ?: "Failed to stop client")
      } finally {
        haClient = null
      }
    }
  }

  fun clean() {
    stopClient()
    clientScope.cancel()
  }
}
