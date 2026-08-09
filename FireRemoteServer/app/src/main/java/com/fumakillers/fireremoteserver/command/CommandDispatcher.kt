package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.protocol.CommandParseException
import com.fumakillers.fireremoteserver.protocol.CommandParser
import com.fumakillers.fireremoteserver.protocol.PreviewResponseJson
import com.fumakillers.fireremoteserver.protocol.RemoteCommand
import com.fumakillers.fireremoteserver.preview.PreviewProvider
import com.fumakillers.fireremoteserver.preview.PreviewResult
import org.json.JSONObject

class CommandDispatcher(
    private val executor: CommandExecutor,
    private val previewProvider: PreviewProvider,
) {
    fun dispatch(rawMessage: String, respond: (String) -> Unit) {
        try {
            val command = CommandParser.parse(rawMessage)
            if (command is RemoteCommand.PreviewRequest) {
                try {
                    previewProvider.capture { result ->
                        respond(PreviewResponseJson.create(command.requestId, result))
                    }
                } catch (_: RuntimeException) {
                    respond(
                        PreviewResponseJson.create(
                            command.requestId,
                            PreviewResult.Error("Preview capture failed"),
                        ),
                    )
                }
            } else {
                executor.execute(command) { result ->
                    respond(response(command.requestId, result.success, result.message))
                }
            }
        } catch (error: CommandParseException) {
            respond(response(null, false, error.message ?: "Invalid command"))
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
