package net.adarw.hassintercom.protocol

interface AudioSource {
    suspend fun start()
    suspend fun stop()
    suspend fun readFrame(): ByteArray
}

interface AudioSink {
    suspend fun start()
    suspend fun stop()
    suspend fun play(frame: ByteArray)
}
