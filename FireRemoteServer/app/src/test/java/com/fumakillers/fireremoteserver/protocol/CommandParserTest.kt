package com.fumakillers.fireremoteserver.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommandParserTest {
    @Test
    fun parsesTapCommand() {
        val command = CommandParser.parse(
            """{"version":1,"type":"tap","requestId":"test-1","x":500,"y":300}""",
        ) as RemoteCommand.Tap

        assertEquals(500, command.x)
        assertEquals(300, command.y)
        assertEquals("test-1", command.requestId)
    }

    @Test
    fun parsesHomeCommand() {
        val command = CommandParser.parse(
            """{"version":1,"type":"home","requestId":"home-1"}""",
        ) as RemoteCommand.Home

        assertEquals("home-1", command.requestId)
    }

    @Test
    fun parsesRecentsCommand() {
        val command = CommandParser.parse(
            """{"version":1,"type":"recents","requestId":"recents-1"}""",
        ) as RemoteCommand.Recents

        assertEquals("recents-1", command.requestId)
    }

    @Test
    fun parsesPreviewRequest() {
        val command = CommandParser.parse(
            """{"version":1,"type":"previewRequest","requestId":"preview-1"}""",
        ) as RemoteCommand.PreviewRequest

        assertEquals("preview-1", command.requestId)
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertThrows(CommandParseException::class.java) {
            CommandParser.parse("""{"version":2,"type":"ping"}""")
        }
    }

    @Test
    fun rejectsShortLongPress() {
        assertThrows(CommandParseException::class.java) {
            CommandParser.parse(
                """{"version":1,"type":"longPress","x":10,"y":20,"durationMs":50}""",
            )
        }
    }

    @Test
    fun parsesLongPressCommand() {
        val command = CommandParser.parse(
            """{"version":1,"type":"longPress","requestId":"hold-1","x":10,"y":20,"durationMs":600}""",
        ) as RemoteCommand.LongPress

        assertEquals(10, command.x)
        assertEquals(20, command.y)
        assertEquals(600, command.durationMs)
        assertEquals("hold-1", command.requestId)
    }

    @Test
    fun parsesSwipeCommand() {
        val command = CommandParser.parse(
            """{"version":1,"type":"swipe","requestId":"swipe-1","startX":10,"startY":20,"endX":30,"endY":40,"durationMs":300}""",
        ) as RemoteCommand.Swipe

        assertEquals(10, command.startX)
        assertEquals(20, command.startY)
        assertEquals(30, command.endX)
        assertEquals(40, command.endY)
        assertEquals(300, command.durationMs)
        assertEquals("swipe-1", command.requestId)
    }

    @Test
    fun rejectsSwipeWithNegativeCoordinate() {
        assertThrows(CommandParseException::class.java) {
            CommandParser.parse(
                """{"version":1,"type":"swipe","startX":-1,"startY":20,"endX":30,"endY":40,"durationMs":300}""",
            )
        }
    }

    @Test
    fun rejectsSwipeWithDurationOutsideProtocolRange() {
        assertThrows(CommandParseException::class.java) {
            CommandParser.parse(
                """{"version":1,"type":"swipe","startX":10,"startY":20,"endX":30,"endY":40,"durationMs":60001}""",
            )
        }
    }

    @Test
    fun rejectsFractionalCoordinate() {
        assertThrows(CommandParseException::class.java) {
            CommandParser.parse("""{"version":1,"type":"tap","x":10.5,"y":20}""")
        }
    }

    @Test
    fun rejectsCoordinateFarBelowIntRange() {
        assertThrows(CommandParseException::class.java) {
            CommandParser.parse("""{"version":1,"type":"tap","x":-4294967296,"y":20}""")
        }
    }
}
