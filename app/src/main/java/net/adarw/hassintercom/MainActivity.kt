package net.adarw.hassintercom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.adarw.hassintercom.protocol.AudioFormat
import net.adarw.hassintercom.utils.StreamPrefs

class MainActivity : ComponentActivity() {

  // Expose a MutableState for UI error messages
  val errorMessage = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { IntercomUI() }
  }

  override fun onDestroy() {
    super.onDestroy()
    Client.clean()
  }

  @Composable
  fun IntercomUI() {
    val context = LocalContext.current
    val streamPrefs = remember { StreamPrefs(context) }

    var host by remember { mutableStateOf(streamPrefs.host) }
    var port by remember { mutableStateOf(streamPrefs.port) }
    var clientId by remember { mutableStateOf(streamPrefs.clientId) }
    var sampleRate by remember { mutableStateOf(streamPrefs.sampleRate) }
    var channels by remember { mutableStateOf(streamPrefs.channels) }
    var frameMs by remember { mutableStateOf(streamPrefs.frameMs) }

    // Automatically save changes
    LaunchedEffect(host, port, clientId, sampleRate, channels, frameMs) {
      streamPrefs.host = host
      streamPrefs.port = port
      streamPrefs.clientId = clientId
      streamPrefs.sampleRate = sampleRate
      streamPrefs.channels = channels
      streamPrefs.frameMs = frameMs
    }
    var isRunning by remember { mutableStateOf(false) }
    var streamId by remember { mutableStateOf(Client.streamId) }

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
                      if (isRunning) Client.stopClient()
                      else
                          Client.startClient(
                              host = host,
                              port = port.toIntOrNull() ?: 8765,
                              clientId = clientId,
                              audioFormat =
                                  AudioFormat(
                                      sampleRate = sampleRate.toIntOrNull() ?: 16000,
                                      channels = channels.toIntOrNull() ?: 1,
                                      frameMs = frameMs.toIntOrNull() ?: 400),
                              this@MainActivity)
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
