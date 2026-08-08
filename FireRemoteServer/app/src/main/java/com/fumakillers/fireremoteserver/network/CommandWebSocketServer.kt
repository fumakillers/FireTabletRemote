package com.fumakillers.fireremoteserver.network

import android.util.Log
import com.fumakillers.fireremoteserver.command.CommandDispatcher
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class CommandWebSocketServer(
    port: Int,
    private val dispatcher: CommandDispatcher,
) : WebSocketServer(InetSocketAddress("0.0.0.0", port)) {
    override fun onOpen(connection: WebSocket, handshake: ClientHandshake) {
        Log.i(TAG, "Client connected: ${connection.remoteSocketAddress}")
    }

    override fun onClose(connection: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.i(TAG, "Client disconnected: code=$code reason=$reason")
    }

    override fun onMessage(connection: WebSocket, message: String) {
        Log.i(TAG, "Message: $message")
        connection.send(dispatcher.dispatch(message))
    }

    override fun onError(connection: WebSocket?, error: Exception) {
        Log.e(TAG, "WebSocket error", error)
    }

    override fun onStart() {
        Log.i(TAG, "WebSocket server listening on port $port")
        connectionLostTimeout = 30
    }

    private companion object {
        const val TAG = "FireRemoteWebSocket"
    }
}
