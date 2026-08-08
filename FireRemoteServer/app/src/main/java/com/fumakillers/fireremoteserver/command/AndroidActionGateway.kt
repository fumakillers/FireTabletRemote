package com.fumakillers.fireremoteserver.command

fun interface AndroidActionGateway {
    fun performBack(): AndroidActionResult
}

sealed interface AndroidActionResult {
    data object Performed : AndroidActionResult
    data object ServiceNotConnected : AndroidActionResult
    data object Rejected : AndroidActionResult
    data object Failed : AndroidActionResult
}
