package net.adarw.hassintercom.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.UUID

class JsonConnection(private val host: String, private val port: Int) {
    private lateinit var socket: Socket
    private lateinit var writer: BufferedWriter
    private lateinit var reader: BufferedReader

    suspend fun connect() = withContext(Dispatchers.IO) {
        socket = Socket(host, port)
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    }

    suspend fun send(message: Map<String, Any>) = withContext(Dispatchers.IO) {
        val json = JSONObject(message).toString()
        writer.write(json + "\n")
        writer.flush()
    }

    suspend fun receive(): JSONObject = withContext(Dispatchers.IO) {
        val line = reader.readLine() ?: throw IOException("Connection closed")
        JSONObject(line)
    }

    fun close() {
        socket.close()
    }
}
