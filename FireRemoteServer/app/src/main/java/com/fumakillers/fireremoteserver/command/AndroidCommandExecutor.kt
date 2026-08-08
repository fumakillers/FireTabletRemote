package com.fumakillers.fireremoteserver.command

import android.util.Log
import com.fumakillers.fireremoteserver.protocol.RemoteCommand

class AndroidCommandExecutor : CommandExecutor {
    override fun execute(command: RemoteCommand): CommandResult {
        Log.i(TAG, "Received command: $command")
        return when (command) {
            is RemoteCommand.Ping -> CommandResult(true, "pong")
            else -> CommandResult(false, "Command parsed; Android operation is not implemented yet")
        }
    }

    private companion object {
        const val TAG = "FireRemoteCommand"
    }
}
