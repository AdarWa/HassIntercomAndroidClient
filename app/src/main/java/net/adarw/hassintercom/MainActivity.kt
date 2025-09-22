package net.adarw.hassintercom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.adarw.hassintercom.protocol.AudioFormat
import net.adarw.hassintercom.protocol.AudioSink
import net.adarw.hassintercom.protocol.AudioSource
import net.adarw.hassintercom.protocol.HomeAssistantClient
import net.adarw.hassintercom.service.MicrophoneAudioSource
import net.adarw.hassintercom.service.NullAudioSink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sourceFactory: (AudioFormat) -> AudioSource = { format ->
            MicrophoneAudioSource(format)
        }

        val sinkFactory: (AudioFormat) -> AudioSink = { format ->
            NullAudioSink(format)
        }

        val audioFormat = AudioFormat(
            sampleRate = 16000,
            channels = 1,
            frameMs = 40
        )

        val haClient = HomeAssistantClient(
            host = "192.168.1.67",       // IP of your intercom server
            port = 8765,                 // TCP port
            clientId = "android-client-1",
            preferredFormat = audioFormat,
            sourceFactory = sourceFactory,
            sinkFactory = sinkFactory,
            autoStart = true,
            requestedStreamId = null
        )

        CoroutineScope(Dispatchers.IO).launch {
            haClient.run()
        }
    }
}