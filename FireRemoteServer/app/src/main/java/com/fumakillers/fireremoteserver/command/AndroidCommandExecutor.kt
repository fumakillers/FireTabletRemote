package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.protocol.RemoteCommand

class AndroidCommandExecutor(
    private val actionGateway: AndroidActionGateway,
) : CommandExecutor {
    override fun execute(command: RemoteCommand): CommandResult = when (command) {
        is RemoteCommand.Ping -> CommandResult(true, "pong")
        is RemoteCommand.Back -> executeBack()
        is RemoteCommand.Tap,
        is RemoteCommand.LongPress,
        -> CommandResult(false, "Command parsed; Android operation is not implemented yet")
    }

    private fun executeBack(): CommandResult = when (actionGateway.performBack()) {
        AndroidActionResult.Performed -> CommandResult(true, "Back performed")
        AndroidActionResult.ServiceNotConnected ->
            CommandResult(false, "Accessibility service is not connected")
        AndroidActionResult.Rejected -> CommandResult(false, "Android rejected the back action")
        AndroidActionResult.Failed -> CommandResult(false, "Back action failed")
    }
}
