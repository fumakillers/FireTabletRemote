package com.fumakillers.fireremoteserver.command

import com.fumakillers.fireremoteserver.protocol.RemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCommandExecutorTest {
    @Test
    fun backFailsWhenAccessibilityServiceIsNotConnected() {
        val executor = executorReturning(AndroidActionResult.ServiceNotConnected)

        val result = execute(executor, RemoteCommand.Back("back-1"))

        assertFalse(result.success)
        assertEquals("Accessibility service is not connected", result.message)
    }

    @Test
    fun backSucceedsWhenGlobalActionSucceeds() {
        var performedAction: AndroidGlobalAction? = null
        val executor = AndroidCommandExecutor(
            actionGateway = { action ->
                performedAction = action
                AndroidActionResult.Performed
            },
            gestureGateway = unusedGestureGateway,
        )

        val result = execute(executor, RemoteCommand.Back("back-1"))

        assertTrue(result.success)
        assertEquals("Back performed", result.message)
        assertEquals(AndroidGlobalAction.BACK, performedAction)
    }

    @Test
    fun homeSucceedsWhenGlobalActionSucceeds() {
        var performedAction: AndroidGlobalAction? = null
        val executor = AndroidCommandExecutor(
            actionGateway = { action ->
                performedAction = action
                AndroidActionResult.Performed
            },
            gestureGateway = unusedGestureGateway,
        )

        val result = execute(executor, RemoteCommand.Home("home-1"))

        assertTrue(result.success)
        assertEquals("Home performed", result.message)
        assertEquals(AndroidGlobalAction.HOME, performedAction)
    }

    @Test
    fun recentsSucceedsWhenGlobalActionSucceeds() {
        var performedAction: AndroidGlobalAction? = null
        val executor = AndroidCommandExecutor(
            actionGateway = { action ->
                performedAction = action
                AndroidActionResult.Performed
            },
            gestureGateway = unusedGestureGateway,
        )

        val result = execute(executor, RemoteCommand.Recents("recents-1"))

        assertTrue(result.success)
        assertEquals("Recents performed", result.message)
        assertEquals(AndroidGlobalAction.RECENTS, performedAction)
    }

    @Test
    fun backFailsWhenGlobalActionIsRejected() {
        val executor = executorReturning(AndroidActionResult.Rejected)

        val result = execute(executor, RemoteCommand.Back("back-1"))

        assertFalse(result.success)
        assertEquals("Android rejected the back action", result.message)
    }

    @Test
    fun backFailsWhenActionGatewayReportsFailure() {
        val executor = executorReturning(AndroidActionResult.Failed)

        val result = execute(executor, RemoteCommand.Back("back-1"))

        assertFalse(result.success)
        assertEquals("Back action failed", result.message)
    }

    @Test
    fun pingDoesNotUseAccessibilityService() {
        var callCount = 0
        val executor = AndroidCommandExecutor(
            actionGateway = {
                callCount++
                AndroidActionResult.ServiceNotConnected
            },
            gestureGateway = unusedGestureGateway,
        )

        val result = execute(executor, RemoteCommand.Ping("ping-1"))

        assertTrue(result.success)
        assertEquals("pong", result.message)
        assertEquals(0, callCount)
    }

    @Test
    fun tapSucceedsWhenGestureCompletes() {
        val executor = executorWithGestureResult(AndroidGestureResult.Completed)

        val result = execute(executor, RemoteCommand.Tap(10, 20, "tap-1"))

        assertTrue(result.success)
        assertEquals("Tap performed", result.message)
    }

    @Test
    fun tapResultWaitsForGestureCompletionCallback() {
        var gestureCallback: ((AndroidGestureResult) -> Unit)? = null
        var commandResult: CommandResult? = null
        val executor = AndroidCommandExecutor(
            actionGateway = { AndroidActionResult.Performed },
            gestureGateway = { _, callback -> gestureCallback = callback },
        )

        executor.execute(RemoteCommand.Tap(10, 20, "tap-1")) { commandResult = it }

        assertNull(commandResult)
        requireNotNull(gestureCallback)(AndroidGestureResult.Completed)
        assertTrue(requireNotNull(commandResult).success)
    }

    @Test
    fun tapReportsServiceNotConnected() {
        val result = execute(
            executorWithGestureResult(AndroidGestureResult.ServiceNotConnected),
            RemoteCommand.Tap(10, 20, "tap-1"),
        )

        assertFalse(result.success)
        assertEquals("Accessibility service is not connected", result.message)
    }

    @Test
    fun tapReportsRejectedAndCancelledGestures() {
        val rejected = execute(
            executorWithGestureResult(AndroidGestureResult.Rejected),
            RemoteCommand.Tap(10, 20, "tap-1"),
        )
        val cancelled = execute(
            executorWithGestureResult(AndroidGestureResult.Cancelled),
            RemoteCommand.Tap(10, 20, "tap-2"),
        )

        assertFalse(rejected.success)
        assertEquals("Android rejected the tap gesture", rejected.message)
        assertFalse(cancelled.success)
        assertEquals("Tap gesture was cancelled", cancelled.message)
    }

    @Test
    fun tapReportsGestureFailure() {
        val result = execute(
            executorWithGestureResult(AndroidGestureResult.Failed),
            RemoteCommand.Tap(10, 20, "tap-1"),
        )

        assertFalse(result.success)
        assertEquals("Tap gesture failed", result.message)
    }

    @Test
    fun longPressSucceedsWhenGestureCompletes() {
        var performedGesture: AndroidGesture? = null
        val executor = AndroidCommandExecutor(
            actionGateway = { AndroidActionResult.Performed },
            gestureGateway = { gesture, callback ->
                performedGesture = gesture
                callback(AndroidGestureResult.Completed)
            },
        )

        val result = execute(
            executor,
            RemoteCommand.LongPress(10, 20, 1_000, "hold-1"),
        )

        assertTrue(result.success)
        assertEquals("Long press performed", result.message)
        assertEquals(AndroidGesture.LongPress(10, 20, 1_000), performedGesture)
    }

    @Test
    fun swipeSucceedsWhenGestureCompletes() {
        var performedGesture: AndroidGesture? = null
        val executor = AndroidCommandExecutor(
            actionGateway = { AndroidActionResult.Performed },
            gestureGateway = { gesture, callback ->
                performedGesture = gesture
                callback(AndroidGestureResult.Completed)
            },
        )

        val result = execute(
            executor,
            RemoteCommand.Swipe(10, 20, 30, 40, 300, "swipe-1"),
        )

        assertTrue(result.success)
        assertEquals("Swipe performed", result.message)
        assertEquals(AndroidGesture.Swipe(10, 20, 30, 40, 300), performedGesture)
    }

    @Test
    fun longPressReportsServiceNotConnected() {
        val result = execute(
            executorWithGestureResult(AndroidGestureResult.ServiceNotConnected),
            RemoteCommand.LongPress(10, 20, 600, "hold-1"),
        )

        assertFalse(result.success)
        assertEquals("Accessibility service is not connected", result.message)
    }

    @Test
    fun swipeReportsRejectedAndCancelledGestures() {
        val rejected = execute(
            executorWithGestureResult(AndroidGestureResult.Rejected),
            RemoteCommand.Swipe(10, 20, 30, 40, 300, "swipe-1"),
        )
        val cancelled = execute(
            executorWithGestureResult(AndroidGestureResult.Cancelled),
            RemoteCommand.Swipe(10, 20, 30, 40, 300, "swipe-2"),
        )

        assertFalse(rejected.success)
        assertEquals("Android rejected the swipe gesture", rejected.message)
        assertFalse(cancelled.success)
        assertEquals("Swipe gesture was cancelled", cancelled.message)
    }

    private fun executorReturning(result: AndroidActionResult) =
        AndroidCommandExecutor({ result }, unusedGestureGateway)

    private fun executorWithGestureResult(result: AndroidGestureResult) =
        AndroidCommandExecutor(
            actionGateway = { AndroidActionResult.Performed },
            gestureGateway = { _, callback -> callback(result) },
        )

    private fun execute(executor: AndroidCommandExecutor, command: RemoteCommand): CommandResult {
        var result: CommandResult? = null
        executor.execute(command) { result = it }
        return requireNotNull(result)
    }

    private companion object {
        val unusedGestureGateway = AndroidGestureGateway { _, _ ->
            error("Gesture gateway should not be called")
        }
    }
}
