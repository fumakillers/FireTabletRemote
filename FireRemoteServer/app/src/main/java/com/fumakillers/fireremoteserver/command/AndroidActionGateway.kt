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
