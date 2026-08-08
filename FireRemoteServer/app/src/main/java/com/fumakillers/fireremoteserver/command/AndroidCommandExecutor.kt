package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.protocol.RemoteCommand

class AndroidCommandExecutor(
    private val actionGateway: AndroidActionGateway,
) : CommandExecutor {
    override fun execute(command: RemoteCommand): CommandResult = when (command) {
        is RemoteCommand.Ping -> CommandResult(true, "pong")
        is RemoteCommand.Back -> executeGlobalAction(AndroidGlobalAction.BACK, "Back", "back")
        is RemoteCommand.Home -> executeGlobalAction(AndroidGlobalAction.HOME, "Home", "home")
        is RemoteCommand.Recents -> executeGlobalAction(AndroidGlobalAction.RECENTS, "Recents", "recents")
        is RemoteCommand.Tap,
        is RemoteCommand.LongPress,
        is RemoteCommand.PreviewRequest,
        -> CommandResult(false, "Command parsed; Android operation is not implemented yet")
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
