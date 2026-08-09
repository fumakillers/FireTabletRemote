package com.fumakillers.fireremoteserver.network

import android.util.Log
import com.fumakillers.fireremoteserver.command.CommandDispatcher
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshakeBuilder
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class CommandWebSocketServer(
    port: Int,
    private val dispatcher: CommandDispatcher,
    private val onListening: () -> Unit = {},
    private val onFatalError: (Exception) -> Unit = {},
) : WebSocketServer(InetSocketAddress("0.0.0.0", port)) {
    override fun onWebsocketHandshakeReceivedAsServer(
        connection: WebSocket,
        draft: Draft,
        request: ClientHandshake,
    ): ServerHandshakeBuilder {
        WebSocketEndpoint.requireAllowed(request.resourceDescriptor)
        return super.onWebsocketHandshakeReceivedAsServer(connection, draft, request)
    }

    override fun onOpen(connection: WebSocket, handshake: ClientHandshake) {
        Log.i(TAG, "Client connected: ${connection.remoteSocketAddress}")
    }

    override fun onClose(connection: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.i(TAG, "Client disconnected: code=$code reason=$reason")
    }

    override fun onMessage(connection: WebSocket, message: String) {
        if (message.contains("\"type\":\"previewRequest\"")) {
            Log.d(PREVIEW_TAG, "previewRequest received")
        } else {
            Log.i(TAG, "Message: $message")
        }
        dispatcher.dispatch(message) { response ->
            if (connection.isOpen) {
                try {
                    connection.send(response)
                } catch (error: RuntimeException) {
                    Log.e(TAG, "Could not send WebSocket response", error)
                }
            }
        }
    }

    override fun onError(connection: WebSocket?, error: Exception) {
        Log.e(TAG, "WebSocket error", error)
        if (connection == null) {
            onFatalError(error)
        }
    }

    override fun onStart() {
        Log.i(TAG, "WebSocket server listening on port $port")
        connectionLostTimeout = 30
        onListening()
    }

    private companion object {
        const val TAG = "FireRemoteWebSocket"
        const val PREVIEW_TAG = "FireRemotePreview"
    }
}
