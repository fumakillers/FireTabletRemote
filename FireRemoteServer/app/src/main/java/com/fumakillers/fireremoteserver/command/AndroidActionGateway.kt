package com.fumakillers.fireremoteserver.command

const val TAP_DURATION_MS = 75L

fun interface AndroidActionGateway {
    fun perform(action: AndroidGlobalAction): AndroidActionResult
}

enum class AndroidGlobalAction {
    BACK,
    HOME,
    RECENTS,
}

sealed interface AndroidActionResult {
    data object Performed : AndroidActionResult
    data object ServiceNotConnected : AndroidActionResult
    data object Rejected : AndroidActionResult
    data object Failed : AndroidActionResult
}

fun interface AndroidGestureGateway {
    fun perform(gesture: AndroidGesture, callback: (AndroidGestureResult) -> Unit)
}

sealed interface AndroidGesture {
    val durationMs: Long

    data class Tap(val x: Int, val y: Int) : AndroidGesture {
        override val durationMs: Long = TAP_DURATION_MS
    }

    data class LongPress(
        val x: Int,
        val y: Int,
        override val durationMs: Long,
    ) : AndroidGesture

    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        override val durationMs: Long,
    ) : AndroidGesture
}

sealed interface AndroidGestureResult {
    data object Completed : AndroidGestureResult
    data object ServiceNotConnected : AndroidGestureResult
    data object Rejected : AndroidGestureResult
    data object Cancelled : AndroidGestureResult
    data object InvalidCoordinates : AndroidGestureResult
    data object Failed : AndroidGestureResult
}
