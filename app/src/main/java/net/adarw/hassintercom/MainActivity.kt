package net.adarw.hassintercom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private val audioFormat = AudioFormat(
        sampleRate = 16000,
        channels = 1,
        frameMs = 40
    )

    private val sourceFactory: (AudioFormat) -> AudioSource = { format ->
        MicrophoneAudioSource(format)
    }

    private val sinkFactory: (AudioFormat) -> AudioSink = { format ->
        SimpleAudioSink(format)
    }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startClient()
            } else {
                Log.w(TAG, "Microphone permission denied; audio disabled")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startClient()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clientScope.cancel()
    }

    private fun startClient() {
        if (haClient != null) return
        val client = HomeAssistantClient(
            host = "192.168.1.67",       // IP of your intercom server
            port = 8765,                 // TCP port
            clientId = "android-client-1",
            preferredFormat = audioFormat,
            sourceFactory = sourceFactory,
            sinkFactory = sinkFactory,
            autoStart = true,
            requestedStreamId = null
        )

        haClient = client
        clientScope.launch {
            client.run()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
