package net.adarw.hassintercom

import org.json.JSONObject


fun JSONObject.toMap(): Map<String, Any> =
    keys().asSequence().associateWith { key ->
        when (val value = this[key]) {
            is JSONObject -> value.toMap()
            else -> value
        }
    }