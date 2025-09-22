package net.adarw.hassintercom.protocol

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.adarw.hassintercom.toMap
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class HomeAssistantClient(
    private val host: String,
    private val port: Int,
    private val clientId: String,
    private val preferredFormat: AudioFormat,
    private val sourceFactory: (AudioFormat) -> AudioSource,
    private val sinkFactory: (AudioFormat) -> AudioSink,
    private val autoStart: Boolean,
    private val requestedStreamId: String? = null
) {
    private var connection: JsonConnection? = null
    private val pending = mutableMapOf<String, PendingCommand>()
    private val streams = mutableMapOf<String, HomeAudioStream>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun run() {
        try {
            connection = JsonConnection(host, port)
            connection!!.connect()
            registerClient()
            if (autoStart) requestStartAudio(requestedStreamId)
            listenLoop()
        } catch (e: Exception) {
            Log.e("HAClient", "Error: ${e.message}", e)
        } finally {
            shutdown()
        }
    }

    private suspend fun registerClient() {
        connection?.send(
            mapOf(
                "type" to "register",
                "role" to "home_assistant",
                "client_id" to clientId
            )
        )
        Log.i("HAClient", "Registered client $clientId")
    }

    suspend fun requestStartAudio(streamId: String? = null) {
        val payload = mutableMapOf(
            "encoding" to preferredFormat.encoding,
            "sample_rate" to preferredFormat.sampleRate,
            "channels" to preferredFormat.channels
        )
        streamId?.let { payload["stream_id"] = it }
        val commandId = UUID.randomUUID().toString()
        sendCommand(commandId, "start_audio", payload)
    }

    suspend fun requestStopAudio(streamId: String) {
        val commandId = UUID.randomUUID().toString()
        sendCommand(commandId, "stop_audio", mapOf("stream_id" to streamId))
    }

    private suspend fun sendCommand(commandId: String, command: String, payload: Map<String, Any>) {
        val conn = connection ?: throw IOException("No connection")
        conn.send(
            mapOf(
                "type" to "command",
                "command" to command,
                "command_id" to commandId,
                "payload" to payload
            )
        )
        pending[commandId] = PendingCommand(command, payload)
    }

    private suspend fun listenLoop() {
        val conn = connection ?: return
        while (true) {
            val msg = conn.receive()
            when (msg.getString("type")) {
                "command_ack" -> handleCommandAck(msg)
                "response" -> handleResponse(msg)
                "event" -> handleEvent(msg)
                "audio_frame" -> handleAudioFrame(msg)
                "error" -> handleError(msg)
                "close" -> break
                else -> Log.d("HAClient", "Unhandled message type")
            }
        }
    }

    private fun handleCommandAck(msg: JSONObject) {
        val commandId = msg.optString("command_id")
        Log.d("HAClient", "Command $commandId acknowledged")
    }

    private fun handleResponse(msg: JSONObject) {
        val commandId = msg.optString("command_id")
        val pendingCmd = pending.remove(commandId) ?: return
        val status = msg.optString("status")
        val payload = msg.optJSONObject("payload")?.toMap() ?: emptyMap()
        scope.launch {
            if (pendingCmd.command == "start_audio" && status == "ok") {
                startStreamFromResponse(payload)
            } else if (pendingCmd.command == "stop_audio" && status == "ok") {
                payload["stream_id"]?.let { terminateStream(it.toString()) }
            }
        }
    }

    private suspend fun startStreamFromResponse(payload: Map<String, Any>) {
        val streamId = payload["stream_id"] as? String ?: return
        val format = AudioFormat(
            encoding = payload["encoding"] as? String ?: "pcm_s16le",
            sampleRate = (payload["sample_rate"] as? Int) ?: 16000,
            channels = (payload["channels"] as? Int) ?: 1,
            frameMs = preferredFormat.frameMs
        )
        val src = sourceFactory(format)
        val sink = sinkFactory(format)
        val stream = HomeAudioStream(streamId, format, src, sink, connection!!)
        streams[streamId] = stream
        stream.job = scope.launch { sourceLoop(stream) }
    }

    private suspend fun sourceLoop(stream: HomeAudioStream) {
        stream.source.start()
        stream.sink.start()
        val frameInterval = stream.format.frameMs
        try {
            while (true) {
                val frame = stream.source.readFrame()
                stream.connection.send(
                    mapOf(
                        "type" to "audio_frame",
                        "stream_id" to stream.streamId,
                        "sequence" to stream.sequence,
                        "encoding" to stream.format.encoding,
                        "sample_rate" to stream.format.sampleRate,
                        "channels" to stream.format.channels,
                        "direction" to "client_to_intercom",
                        "data" to Base64.encodeToString(frame, Base64.NO_WRAP)
                    )
                )
                stream.sequence += 1
                delay((frameInterval).toLong())
            }
        } catch (e: CancellationException) {
            // stream stopped
        } finally {
            stream.source.stop()
            stream.sink.stop()
            streams.remove(stream.streamId)
        }
    }

    private fun handleAudioFrame(msg: JSONObject) {
        val streamId = msg.optString("stream_id") ?: return
        val stream = streams[streamId] ?: return
        val direction = msg.optString("direction")
        if (direction != "intercom_to_client") return
        val dataStr = msg.optString("data") ?: return
        val frame = Base64.decode(dataStr.toByteArray(Charsets.US_ASCII), Base64.DEFAULT)
        stream.sink.play(frame)
    }

    private fun handleEvent(msg: JSONObject) = Log.i("HAClient", "Event: ${msg.optString("event")}")
    private fun handleError(msg: JSONObject) {
        val details = msg.optJSONObject("details")?.toMap() ?: emptyMap()
        val streamId = details["stream_id"] as? String
        scope.launch { streamId?.let { terminateStream(it) } }
    }

    private suspend fun terminateStream(streamId: String) {
        val stream = streams.remove(streamId) ?: return
        stream.job?.cancelAndJoin()
        stream.source.stop()
        stream.sink.stop()
    }

    private suspend fun shutdown() {
        streams.keys.toList().forEach { terminateStream(it) }
        connection?.close()
    }
}
