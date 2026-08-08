package com.fumakillers.fireremoteserver.network

import org.java_websocket.exceptions.InvalidDataException
import org.java_websocket.framing.CloseFrame

internal object WebSocketEndpoint {
    const val PATH = "/ws"

    fun requireAllowed(resourceDescriptor: String) {
        if (resourceDescriptor != PATH) {
            throw InvalidDataException(
                CloseFrame.POLICY_VALIDATION,
                "WebSocket endpoint must be $PATH",
            )
        }
    }
}
