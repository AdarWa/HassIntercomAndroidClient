package net.adarw.hassintercom

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

class MainActivity : ComponentActivity() {

  private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var haClient: HomeAssistantClient? = null
  private var streamId = UUID.randomUUID().toString()

  private val audioFormat = AudioFormat(sampleRate = 16000, channels = 1, frameMs = 400)
  private val sourceFactory: (AudioFormat) -> AudioSource = { MicrophoneAudioSource(it) }
  private val sinkFactory: (AudioFormat) -> AudioSink = { SimpleAudioSink(it) }

  // Expose a MutableState for UI error messages
  val errorMessage = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { IntercomUI() }
  }

  override fun onDestroy() {
    super.onDestroy()
    stopClient()
    clientScope.cancel()
  }

  private fun startClient(host: String, port: Int, clientId: String, audioFormat: AudioFormat) {
    if (haClient != null) return
    try {
      val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
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
          errorMessage.value = e.message ?: "Unknown error in client.run()"
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      errorMessage.value = e.message ?: "Failed to start client"
    }
  }

  private fun stopClient() {
    clientScope.launch {
      try {
        haClient?.requestStopAudio(streamId)
      } catch (e: Exception) {
        e.printStackTrace()
        errorMessage.value = e.message ?: "Failed to stop client"
      } finally {
        haClient = null
      }
    }
  }

  @Composable
  fun IntercomUI() {
    var host by remember { mutableStateOf("192.168.1.67") }
    var port by remember { mutableStateOf("8765") }
    var clientId by remember { mutableStateOf("android-client-1") }
    var sampleRate by remember { mutableStateOf("16000") }
    var channels by remember { mutableStateOf("1") }
    var frameMs by remember { mutableStateOf("400") }
    var isRunning by remember { mutableStateOf(false) }
    var streamId by remember { mutableStateOf(this@MainActivity.streamId) }

    var hasAudioPermission by remember {
      mutableStateOf(
          ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) ==
              PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
              hasAudioPermission = granted
              if (!granted) {
                errorMessage.value = "Microphone permission is required to start the intercom."
              }
            })

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("Intercom Settings", style = MaterialTheme.typography.titleLarge)

          OutlinedTextField(
              value = host,
              onValueChange = { host = it },
              label = { Text("Host") },
              singleLine = true)
          OutlinedTextField(
              value = port,
              onValueChange = { port = it.filter { c -> c.isDigit() } },
              label = { Text("Port") },
              singleLine = true)
          OutlinedTextField(
              value = clientId,
              onValueChange = { clientId = it },
              label = { Text("Client ID") },
              singleLine = true)

          Text("Audio Format", style = MaterialTheme.typography.titleMedium)
          OutlinedTextField(
              value = sampleRate,
              onValueChange = { sampleRate = it.filter { c -> c.isDigit() } },
              label = { Text("Sample Rate") },
              singleLine = true)
          OutlinedTextField(
              value = channels,
              onValueChange = { channels = it.filter { c -> c.isDigit() } },
              label = { Text("Channels") },
              singleLine = true)
          OutlinedTextField(
              value = frameMs,
              onValueChange = { frameMs = it.filter { c -> c.isDigit() } },
              label = { Text("Frame Size (ms)") },
              singleLine = true)

          Text(text = streamId)

          Button(
              onClick = {
                if (!hasAudioPermission) {
                  permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  return@Button
                }

                runCatching {
                      if (isRunning) stopClient()
                      else
                          startClient(
                              host = host,
                              port = port.toIntOrNull() ?: 8765,
                              clientId = clientId,
                              audioFormat =
                                  AudioFormat(
                                      sampleRate = sampleRate.toIntOrNull() ?: 16000,
                                      channels = channels.toIntOrNull() ?: 1,
                                      frameMs = frameMs.toIntOrNull() ?: 400))
                      isRunning = !isRunning
                    }
                    .onFailure {
                      it.printStackTrace()
                      errorMessage.value = it.message ?: "Unknown error"
                    }
              },
              modifier = Modifier.fillMaxWidth()) {
                Text(if (isRunning) "Stop" else "Start")
              }
        }

    // Show message box on error
    if (errorMessage.value != null) {
      AlertDialog(
          onDismissRequest = { errorMessage.value = null },
          confirmButton = { TextButton(onClick = { errorMessage.value = null }) { Text("OK") } },
          title = { Text("Error") },
          text = { Text(errorMessage.value ?: "Unknown error") })
    }
  }
}
