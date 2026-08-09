package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.preview.PreviewProvider
import com.fumakillers.fireremoteserver.preview.PreviewResult
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CommandDispatcherTest {
    @Test
    fun previewRequestReturnsFrameFromProvider() {
        val jpeg = byteArrayOf(1, 2, 3, 4)
        val dispatcher = dispatcherWith(
            PreviewResult.Frame(
                width = 640,
                height = 400,
                sourceWidth = 1920,
                sourceHeight = 1200,
                jpegBytes = jpeg,
            ),
        )

        val response = dispatch(
            dispatcher,
            """{"version":1,"type":"previewRequest","requestId":"preview-1"}""",
        )
        val json = JSONObject(response)

        assertEquals(1, json.getInt("version"))
        assertEquals("previewFrame", json.getString("type"))
        assertEquals("preview-1", json.getString("requestId"))
        assertEquals("image/jpeg", json.getString("mimeType"))
        assertEquals(640, json.getInt("width"))
        assertEquals(400, json.getInt("height"))
        assertEquals(1920, json.getInt("sourceWidth"))
        assertEquals(1200, json.getInt("sourceHeight"))
        assertArrayEquals(jpeg, Base64.getDecoder().decode(json.getString("data")))
    }

    @Test
    fun previewRequestReturnsDedicatedErrorFromProvider() {
        val dispatcher = dispatcherWith(
            PreviewResult.Error("Accessibility service is not connected"),
        )

        val json = JSONObject(
            dispatch(
                dispatcher,
                """{"version":1,"type":"previewRequest","requestId":"preview-2"}""",
            ),
        )

        assertEquals("previewError", json.getString("type"))
        assertEquals("preview-2", json.getString("requestId"))
        assertEquals("Accessibility service is not connected", json.getString("message"))
    }

    @Test
    fun pingStillUsesNormalCommandResult() {
        var providerCalled = false
        val dispatcher = CommandDispatcher(
            executor = { _, callback -> callback(CommandResult(true, "pong")) },
            previewProvider = PreviewProvider {
                providerCalled = true
            },
        )

        val json = JSONObject(
            dispatch(dispatcher, """{"version":1,"type":"ping","requestId":"ping-1"}"""),
        )

        assertEquals("result", json.getString("type"))
        assertEquals("ping-1", json.getString("requestId"))
        assertTrue(json.getBoolean("success"))
        assertEquals("pong", json.getString("message"))
        assertFalse(providerCalled)
    }

    private fun dispatcherWith(result: PreviewResult) = CommandDispatcher(
        executor = { _, callback -> callback(CommandResult(false, "Unexpected command")) },
        previewProvider = PreviewProvider { callback -> callback(result) },
    )

    private fun dispatch(dispatcher: CommandDispatcher, message: String): String {
        var response: String? = null
        dispatcher.dispatch(message) { response = it }
        return requireNotNull(response)
    }
}
