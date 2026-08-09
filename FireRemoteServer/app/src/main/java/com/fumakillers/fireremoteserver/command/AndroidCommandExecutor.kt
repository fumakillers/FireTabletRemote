package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.protocol.RemoteCommand

class AndroidCommandExecutor(
    private val actionGateway: AndroidActionGateway,
    private val gestureGateway: AndroidGestureGateway,
) : CommandExecutor {
    override fun execute(command: RemoteCommand, callback: (CommandResult) -> Unit) {
        when (command) {
            is RemoteCommand.Ping -> callback(CommandResult(true, "pong"))
            is RemoteCommand.Back -> callback(
                executeGlobalAction(AndroidGlobalAction.BACK, "Back", "back"),
            )
            is RemoteCommand.Home -> callback(
                executeGlobalAction(AndroidGlobalAction.HOME, "Home", "home"),
            )
            is RemoteCommand.Recents -> callback(
                executeGlobalAction(AndroidGlobalAction.RECENTS, "Recents", "recents"),
            )
            is RemoteCommand.Tap -> executeGesture(
                AndroidGesture.Tap(command.x, command.y),
                "Tap",
                "tap",
                callback,
            )
            is RemoteCommand.LongPress -> executeGesture(
                AndroidGesture.LongPress(command.x, command.y, command.durationMs),
                "Long press",
                "long press",
                callback,
            )
            is RemoteCommand.Swipe -> executeGesture(
                AndroidGesture.Swipe(
                    command.startX,
                    command.startY,
                    command.endX,
                    command.endY,
                    command.durationMs,
                ),
                "Swipe",
                "swipe",
                callback,
            )
            is RemoteCommand.PreviewRequest ->
                callback(CommandResult(false, "Preview requests are handled separately"))
        }
    }

    private fun executeGesture(
        gesture: AndroidGesture,
        displayName: String,
        protocolName: String,
        callback: (CommandResult) -> Unit,
    ) {
        gestureGateway.perform(gesture) { result ->
            callback(gestureResult(result, displayName, protocolName))
        }
    }

    private fun gestureResult(
        result: AndroidGestureResult,
        displayName: String,
        protocolName: String,
    ): CommandResult = when (result) {
        AndroidGestureResult.Completed -> CommandResult(true, "$displayName performed")
        AndroidGestureResult.ServiceNotConnected ->
            CommandResult(false, "Accessibility service is not connected")
        AndroidGestureResult.Rejected ->
            CommandResult(false, "Android rejected the $protocolName gesture")
        AndroidGestureResult.Cancelled -> CommandResult(false, "$displayName gesture was cancelled")
        AndroidGestureResult.InvalidCoordinates ->
            CommandResult(false, "$displayName coordinates are outside the screen")
        AndroidGestureResult.Failed -> CommandResult(false, "$displayName gesture failed")
    }

    private fun executeGlobalAction(
        action: AndroidGlobalAction,
        displayName: String,
        protocolName: String,
    ): CommandResult = when (actionGateway.perform(action)) {
        AndroidActionResult.Performed -> CommandResult(true, "$displayName performed")
        AndroidActionResult.ServiceNotConnected ->
            CommandResult(false, "Accessibility service is not connected")
        AndroidActionResult.Rejected -> CommandResult(false, "Android rejected the $protocolName action")
        AndroidActionResult.Failed -> CommandResult(false, "$displayName action failed")
    }
}
