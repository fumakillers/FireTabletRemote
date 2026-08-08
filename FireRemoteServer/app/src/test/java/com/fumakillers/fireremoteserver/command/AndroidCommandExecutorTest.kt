package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.protocol.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCommandExecutorTest {
    @Test
    fun backFailsWhenAccessibilityServiceIsNotConnected() {
        val executor = executorReturning(AndroidActionResult.ServiceNotConnected)

        val result = executor.execute(RemoteCommand.Back("back-1"))

        assertFalse(result.success)
        assertEquals("Accessibility service is not connected", result.message)
    }

    @Test
    fun backSucceedsWhenGlobalActionSucceeds() {
        var performedAction: AndroidGlobalAction? = null
        val executor = AndroidCommandExecutor { action ->
            performedAction = action
            AndroidActionResult.Performed
        }

        val result = executor.execute(RemoteCommand.Back("back-1"))

        assertTrue(result.success)
        assertEquals("Back performed", result.message)
        assertEquals(AndroidGlobalAction.BACK, performedAction)
    }

    @Test
    fun homeSucceedsWhenGlobalActionSucceeds() {
        var performedAction: AndroidGlobalAction? = null
        val executor = AndroidCommandExecutor { action ->
            performedAction = action
            AndroidActionResult.Performed
        }

        val result = executor.execute(RemoteCommand.Home("home-1"))

        assertTrue(result.success)
        assertEquals("Home performed", result.message)
        assertEquals(AndroidGlobalAction.HOME, performedAction)
    }

    @Test
    fun recentsSucceedsWhenGlobalActionSucceeds() {
        var performedAction: AndroidGlobalAction? = null
        val executor = AndroidCommandExecutor { action ->
            performedAction = action
            AndroidActionResult.Performed
        }

        val result = executor.execute(RemoteCommand.Recents("recents-1"))

        assertTrue(result.success)
        assertEquals("Recents performed", result.message)
        assertEquals(AndroidGlobalAction.RECENTS, performedAction)
    }

    @Test
    fun backFailsWhenGlobalActionIsRejected() {
        val executor = executorReturning(AndroidActionResult.Rejected)

        val result = executor.execute(RemoteCommand.Back("back-1"))

        assertFalse(result.success)
        assertEquals("Android rejected the back action", result.message)
    }

    @Test
    fun backFailsWhenActionGatewayReportsFailure() {
        val executor = executorReturning(AndroidActionResult.Failed)

        val result = executor.execute(RemoteCommand.Back("back-1"))

        assertFalse(result.success)
        assertEquals("Back action failed", result.message)
    }

    @Test
    fun pingDoesNotUseAccessibilityService() {
        var callCount = 0
        val executor = AndroidCommandExecutor {
            callCount++
            AndroidActionResult.ServiceNotConnected
        }

        val result = executor.execute(RemoteCommand.Ping("ping-1"))

        assertTrue(result.success)
        assertEquals("pong", result.message)
        assertEquals(0, callCount)
    }

    @Test
    fun tapAndLongPressRemainUnimplemented() {
        var callCount = 0
        val executor = AndroidCommandExecutor {
            callCount++
            AndroidActionResult.Performed
        }

        val tapResult = executor.execute(RemoteCommand.Tap(10, 20, "tap-1"))
        val longPressResult = executor.execute(RemoteCommand.LongPress(10, 20, 1_000, "hold-1"))

        assertFalse(tapResult.success)
        assertFalse(longPressResult.success)
        assertEquals(0, callCount)
    }

    private fun executorReturning(result: AndroidActionResult) =
        AndroidCommandExecutor { result }
}
