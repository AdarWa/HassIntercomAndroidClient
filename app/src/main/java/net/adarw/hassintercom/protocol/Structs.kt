package net.adarw.hassintercom.protocol

data class PendingCommand(
    val command: String,
    val payload: Map<String, Any>
)

data class AudioFormat(
    val encoding: String = "pcm_s16le",
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val frameMs: Int = 40
){
    fun bufferSize(): Int {
        val bytesPerSample = when (encoding) {
            "pcm_s16le" -> 2
            else -> error("Unsupported encoding: $encoding")
        }
        val samplesPerFrame = (sampleRate * frameMs) / 1000
        return samplesPerFrame * channels * bytesPerSample
    }

}

data class HomeAudioStream(
    val streamId: String,
    val format: AudioFormat,
    val source: AudioSource,
    val sink: AudioSink,
    val connection: JsonConnection,
    var sequence: Int = 0,
    var job: kotlinx.coroutines.Job? = null
)
