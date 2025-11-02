package net.adarw.hassintercom.utils

import android.content.Context
import androidx.core.content.edit

class StreamPrefs(context: Context) {
  private val prefs = context.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)

  var host: String
    get() = prefs.getString("host", "192.168.1.67") ?: "192.168.1.67"
    set(value) = prefs.edit { putString("host", value) }

  var port: String
    get() = prefs.getString("port", "8765") ?: "8765"
    set(value) = prefs.edit { putString("port", value) }

  var clientId: String
    get() = prefs.getString("clientId", "android-client-1") ?: "android-client-1"
    set(value) = prefs.edit { putString("clientId", value) }

  var sampleRate: String
    get() = prefs.getString("sampleRate", "16000") ?: "16000"
    set(value) = prefs.edit { putString("sampleRate", value) }

  var channels: String
    get() = prefs.getString("channels", "1") ?: "1"
    set(value) = prefs.edit { putString("channels", value) }

  var frameMs: String
    get() = prefs.getString("frameMs", "400") ?: "400"
    set(value) = prefs.edit { putString("frameMs", value) }
}