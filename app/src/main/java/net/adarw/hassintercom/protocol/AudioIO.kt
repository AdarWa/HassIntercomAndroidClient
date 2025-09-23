package net.adarw.hassintercom.protocol

interface AudioSource {
  fun start()

  fun stop()

  fun readFrame(): ByteArray
}

interface AudioSink {
  fun start()

  fun stop()

  fun play(frame: ByteArray)
}
