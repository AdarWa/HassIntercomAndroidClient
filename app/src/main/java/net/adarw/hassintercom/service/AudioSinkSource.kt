package net.adarw.hassintercom.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import androidx.annotation.RequiresPermission
import net.adarw.hassintercom.protocol.AudioSink
import net.adarw.hassintercom.protocol.AudioSource

class MicrophoneAudioSource(private val format: net.adarw.hassintercom.protocol.AudioFormat) :
    AudioSource {
  private var recorder: AudioRecord? = null
  private var bufferSize: Int = 0
  private var echoCanceler: AcousticEchoCanceler? = null

  @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
  override fun start() {
    try {
      bufferSize = format.bufferSize()
      if (bufferSize <= 0) {
        Log.e(MICROPHONE_TAG, "Invalid AudioRecord buffer size: $bufferSize")
        bufferSize = 0
        return
      }
      val audioRecord =
          AudioRecord(
              MediaRecorder.AudioSource.VOICE_COMMUNICATION,
              format.sampleRate,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
              bufferSize)
      enableEchoCanceler(audioRecord)
      recorder = audioRecord
      audioRecord.startRecording()
    } catch (se: SecurityException) {
      Log.e(MICROPHONE_TAG, "RECORD_AUDIO permission is missing; cannot start microphone", se)
      throw se
      cleanupRecorder()
    } catch (e: IllegalStateException) {
      Log.e(MICROPHONE_TAG, "Failed to start AudioRecord", e)
      throw e
      cleanupRecorder()
    }
  }

  override fun stop() {
    try {
      recorder?.stop()
    } catch (e: IllegalStateException) {
      Log.w(MICROPHONE_TAG, "AudioRecord.stop() called before start", e)
    } finally {
      cleanupRecorder()
    }
  }

  override fun readFrame(): ByteArray {
    val currentRecorder = recorder ?: return ByteArray(0)
    if (bufferSize <= 0) {
      return ByteArray(0)
    }
    val buffer = ByteArray(bufferSize)
    val read = currentRecorder.read(buffer, 0, buffer.size)
    if (read <= 0) {
      if (read < 0) {
        Log.w(MICROPHONE_TAG, "AudioRecord read error code: $read")
      }
      return ByteArray(0)
    }
    buffer.copyOf(read)

    return buffer
  }

  private fun cleanupRecorder() {
    recorder?.release()
    recorder = null
    echoCanceler?.release()
    echoCanceler = null
    bufferSize = 0
  }

  private fun enableEchoCanceler(audioRecord: AudioRecord) {
    if (!AcousticEchoCanceler.isAvailable()) {
      Log.i(MICROPHONE_TAG, "Acoustic echo canceller not available on this device")
      throw IllegalStateException("Acoustic echo canceller not available on this device")
      return
    } else {
      Log.i(MICROPHONE_TAG, "echo canceller is available.")
    }
    try {
      echoCanceler?.release()
      val canceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
      if (canceler == null) {
        Log.w(MICROPHONE_TAG, "Failed to create AcousticEchoCanceler")
        echoCanceler = null
        throw IllegalStateException("Failed to create AcousticEchoCanceler")
        return
      }
      canceler.enabled = true
      if (!canceler.enabled) {
        Log.w(MICROPHONE_TAG, "AcousticEchoCanceler failed to enable")
        canceler.release()
        echoCanceler = null
        throw IllegalStateException("AcousticEchoCanceler failed to enable")
        return
      }
      echoCanceler = canceler
    } catch (e: RuntimeException) {
      Log.e(MICROPHONE_TAG, "Error enabling AcousticEchoCanceler", e)
      throw e
      echoCanceler = null
    }
  }

  companion object {
    private const val MICROPHONE_TAG = "MicrophoneAudioSource"
  }
}

class SimpleAudioSink(private val format: net.adarw.hassintercom.protocol.AudioFormat) : AudioSink {
  private var audioTrack: AudioTrack? = null
  private var bufferSize: Int = 0
  private val lock = Any()

  override fun start() {
    bufferSize = format.bufferSize()
    if (bufferSize <= 0) {
      Log.e(SINK_TAG, "Invalid AudioTrack buffer size: $bufferSize")
      bufferSize = 0
      return
    }
    val track =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    if (track.state != AudioTrack.STATE_INITIALIZED) {
      Log.e(SINK_TAG, "AudioTrack failed to initialize; state=${track.state}")
      track.release()
      audioTrack = null
      bufferSize = 0
      return
    }
    audioTrack = track
    track.play()
  }

  override fun stop() {
    val track = synchronized(lock) { audioTrack } ?: return

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

  override fun play(frame: ByteArray) {
    if (frame.isEmpty() || frame.size % 2 != 0) {
      Log.w(SINK_TAG, "Skipping invalid audio frame, size=${frame.size}")
      return
    }

    val track = synchronized(lock) { audioTrack }
    if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
      Log.w(SINK_TAG, "AudioTrack not ready; dropping frame")
      return
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
