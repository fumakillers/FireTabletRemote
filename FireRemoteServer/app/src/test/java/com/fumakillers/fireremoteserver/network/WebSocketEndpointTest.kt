package com.fumakillers.fireremoteserver.network

import org.java_websocket.exceptions.InvalidDataException
import org.java_websocket.framing.CloseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebSocketEndpointTest {
    @Test
    fun acceptsWsEndpoint() {
        WebSocketEndpoint.requireAllowed("/ws")
    }

    @Test
    fun rejectsOtherEndpoints() {
        listOf("/", "/ws/", "/ws?client=test").forEach { endpoint ->
            val error = assertThrows(InvalidDataException::class.java) {
                WebSocketEndpoint.requireAllowed(endpoint)
            }
            assertEquals(CloseFrame.POLICY_VALIDATION, error.closeCode)
        }
    }
}
