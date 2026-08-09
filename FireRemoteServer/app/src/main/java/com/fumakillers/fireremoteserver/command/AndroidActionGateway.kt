package com.fumakillers.fireremoteserver.command

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
    fun performTap(x: Int, y: Int, callback: (AndroidGestureResult) -> Unit)
}

sealed interface AndroidGestureResult {
    data object Completed : AndroidGestureResult
    data object ServiceNotConnected : AndroidGestureResult
    data object Rejected : AndroidGestureResult
    data object Cancelled : AndroidGestureResult
    data object InvalidCoordinates : AndroidGestureResult
    data object Failed : AndroidGestureResult
}
