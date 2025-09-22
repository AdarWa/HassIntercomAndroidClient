package net.adarw.hassintercom.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.adarw.hassintercom.protocol.AudioSink
import net.adarw.hassintercom.protocol.AudioSource

class MicrophoneAudioSource(private val format: net.adarw.hassintercom.protocol.AudioFormat) : AudioSource {
    private var recorder: AudioRecord? = null
    private var bufferSize: Int = 0

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        bufferSize = AudioRecord.getMinBufferSize(
            format.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            format.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        recorder?.startRecording()
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    override suspend fun readFrame(): ByteArray = withContext(Dispatchers.IO) {
        val buffer = ByteArray(bufferSize)
        val read = recorder?.read(buffer, 0, buffer.size) ?: 0
        buffer.copyOf(read)
    }
}

class SimpleAudioSink(private val format: net.adarw.hassintercom.protocol.AudioFormat) : AudioSink {
    private var audioTrack: AudioTrack? = null
    private var bufferSize: Int = 0

    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        bufferSize = AudioTrack.getMinBufferSize(
            format.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override suspend fun play(frame: ByteArray): Unit = withContext(Dispatchers.IO) {
        if (frame.isEmpty()) {
            Log.w("SimpleAudioSink", "Skipping null frame")
            return@withContext
        }
        audioTrack?.write(frame, 0, frame.size)
    }
}

class NullAudioSink(private val format: net.adarw.hassintercom.protocol.AudioFormat) : AudioSink{
    override suspend fun start() {

    }

    override suspend fun stop() {

    }

    override suspend fun play(frame: ByteArray) {

    }

}
