package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.protocol.CommandParseException
import com.fumakillers.fireremoteserver.protocol.CommandParser
import org.json.JSONObject

class CommandDispatcher(private val executor: CommandExecutor) {
    fun dispatch(rawMessage: String): String {
        return try {
            val command = CommandParser.parse(rawMessage)
            val result = executor.execute(command)
            response(command.requestId, result.success, result.message)
        } catch (error: CommandParseException) {
            response(null, false, error.message ?: "Invalid command")
        }
    }

    private fun response(requestId: String?, success: Boolean, message: String): String =
        JSONObject()
            .put("version", 1)
            .put("type", "result")
            .apply { if (requestId != null) put("requestId", requestId) }
            .put("success", success)
            .put("message", message)
            .toString()
}
