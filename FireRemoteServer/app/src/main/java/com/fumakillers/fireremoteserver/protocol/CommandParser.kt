package com.fumakillers.fireremoteserver.protocol

import org.json.JSONException
import org.json.JSONObject

class CommandParseException(message: String) : Exception(message)

object CommandParser {
    fun parse(message: String): RemoteCommand {
        val json = try {
            JSONObject(message)
        } catch (_: JSONException) {
            throw CommandParseException("Message is not valid JSON")
        }

        val version = json.optInt("version", -1)
        if (version != 1) throw CommandParseException("Unsupported protocol version: $version")

        val requestId = json.optString("requestId").takeIf { it.isNotBlank() }
        return when (val type = json.optString("type")) {
            "ping" -> RemoteCommand.Ping(requestId)
            "tap" -> RemoteCommand.Tap(
                x = requiredCoordinate(json, "x"),
                y = requiredCoordinate(json, "y"),
                requestId = requestId,
            )
            "back" -> RemoteCommand.Back(requestId)
            "longPress" -> RemoteCommand.LongPress(
                x = requiredCoordinate(json, "x"),
                y = requiredCoordinate(json, "y"),
                durationMs = requiredDuration(json),
                requestId = requestId,
            )
            else -> throw CommandParseException("Unknown command type: $type")
        }
    }

    private fun requiredCoordinate(json: JSONObject, name: String): Int {
        if (!json.has(name) || json.isNull(name)) throw CommandParseException("Missing $name")
        val number = try { json.get(name) as? Number } catch (_: JSONException) { null }
            ?: throw CommandParseException("$name must be an integer")
        val longValue = number.toLong()
        if (number.toDouble() != longValue.toDouble() || longValue > Int.MAX_VALUE) {
            throw CommandParseException("$name must be an integer")
        }
        val value = longValue.toInt()
        if (value < 0) throw CommandParseException("$name must be zero or greater")
        return value
    }

    private fun requiredDuration(json: JSONObject): Long {
        if (!json.has("durationMs") || json.isNull("durationMs")) {
            throw CommandParseException("Missing durationMs")
        }
        val number = try { json.get("durationMs") as? Number } catch (_: JSONException) { null }
            ?: throw CommandParseException("durationMs must be an integer")
        val value = number.toLong()
        if (number.toDouble() != value.toDouble()) {
            throw CommandParseException("durationMs must be an integer")
        }
        if (value !in 100..60_000) {
            throw CommandParseException("durationMs must be between 100 and 60000")
        }
        return value
    }
}
