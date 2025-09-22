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
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                format.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferSize <= 0) {
                Log.e(MICROPHONE_TAG, "Invalid AudioRecord buffer size: $minBufferSize")
                bufferSize = 0
                return@withContext
            }
            bufferSize = minBufferSize
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                format.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            recorder = audioRecord
            audioRecord.startRecording()
        } catch (se: SecurityException) {
            Log.e(MICROPHONE_TAG, "RECORD_AUDIO permission is missing; cannot start microphone", se)
            cleanupRecorder()
        } catch (e: IllegalStateException) {
            Log.e(MICROPHONE_TAG, "Failed to start AudioRecord", e)
            cleanupRecorder()
        }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        try {
            recorder?.stop()
        } catch (e: IllegalStateException) {
            Log.w(MICROPHONE_TAG, "AudioRecord.stop() called before start", e)
        } finally {
            cleanupRecorder()
        }
    }

    override suspend fun readFrame(): ByteArray = withContext(Dispatchers.IO) {
        val currentRecorder = recorder ?: return@withContext ByteArray(0)
        if (bufferSize <= 0) {
            return@withContext ByteArray(0)
        }
        val buffer = ByteArray(bufferSize)
        val read = currentRecorder.read(buffer, 0, buffer.size)
        if (read <= 0) {
            if (read < 0) {
                Log.w(MICROPHONE_TAG, "AudioRecord read error code: $read")
            }
            return@withContext ByteArray(0)
        }
        buffer.copyOf(read)
    }

    private fun cleanupRecorder() {
        recorder?.release()
        recorder = null
        bufferSize = 0
    }

    companion object {
        private const val MICROPHONE_TAG = "MicrophoneAudioSource"
    }
}

class SimpleAudioSink(private val format: net.adarw.hassintercom.protocol.AudioFormat) : AudioSink {
    private var audioTrack: AudioTrack? = null
    private var bufferSize: Int = 0
    private val lock = Any()


    override suspend fun start(): Unit = withContext(Dispatchers.IO) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            format.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            Log.e(SINK_TAG, "Invalid AudioTrack buffer size: $minBufferSize")
            bufferSize = 0
            return@withContext
        }
        bufferSize = 1280
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(SINK_TAG, "AudioTrack failed to initialize; state=${track.state}")
            track.release()
            audioTrack = null
            bufferSize = 0
            return@withContext
        }
        audioTrack = track
        track.play()
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        val track = synchronized(lock) { audioTrack } ?: return@withContext

        try {
            track.stop()
        } catch (e: IllegalStateException) {
            Log.w(SINK_TAG, "AudioTrack.stop() called before start", e)
        } finally {
            track.release()
            audioTrack = null
            bufferSize = 0
        }
    }

    override suspend fun play(frame: ByteArray) = withContext(Dispatchers.IO) {
        if (frame.isEmpty() || frame.size % 2 != 0) {
            Log.w(SINK_TAG, "Skipping invalid audio frame, size=${frame.size}")
            return@withContext
        }

        val track = synchronized(lock) { audioTrack }
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(SINK_TAG, "AudioTrack not ready; dropping frame")
            return@withContext
        }
        Log.d(SINK_TAG, bufferSize.toString() + " " + frame.size)
        val written = track.write(frame, 0, frame.size)
        if (written < 0) {
            Log.e(SINK_TAG, "AudioTrack.write failed with code $written")
        }
    }


    companion object {
        private const val SINK_TAG = "SimpleAudioSink"
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
