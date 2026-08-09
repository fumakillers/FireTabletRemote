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
            is RemoteCommand.Tap -> gestureGateway.performTap(command.x, command.y) { result ->
                callback(tapResult(result))
            }
            is RemoteCommand.LongPress,
            is RemoteCommand.PreviewRequest,
            -> callback(CommandResult(false, "Command parsed; Android operation is not implemented yet"))
        }
    }

    private fun tapResult(result: AndroidGestureResult): CommandResult = when (result) {
        AndroidGestureResult.Completed -> CommandResult(true, "Tap performed")
        AndroidGestureResult.ServiceNotConnected ->
            CommandResult(false, "Accessibility service is not connected")
        AndroidGestureResult.Rejected -> CommandResult(false, "Android rejected the tap gesture")
        AndroidGestureResult.Cancelled -> CommandResult(false, "Tap gesture was cancelled")
        AndroidGestureResult.InvalidCoordinates -> CommandResult(false, "Tap coordinates are outside the screen")
        AndroidGestureResult.Failed -> CommandResult(false, "Tap gesture failed")
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
