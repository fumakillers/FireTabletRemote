package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.protocol.RemoteCommand

data class CommandResult(val success: Boolean, val message: String)

fun interface CommandExecutor {
    fun execute(command: RemoteCommand): CommandResult
}
