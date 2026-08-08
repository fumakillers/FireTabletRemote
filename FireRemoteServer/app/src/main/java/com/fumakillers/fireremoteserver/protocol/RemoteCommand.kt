package com.fumakillers.fireremoteserver.protocol

sealed interface RemoteCommand {
    val requestId: String?

    data class Ping(override val requestId: String?) : RemoteCommand
    data class Tap(val x: Int, val y: Int, override val requestId: String?) : RemoteCommand
    data class Back(override val requestId: String?) : RemoteCommand
    data class Home(override val requestId: String?) : RemoteCommand
    data class Recents(override val requestId: String?) : RemoteCommand
    data class LongPress(
        val x: Int,
        val y: Int,
        val durationMs: Long,
        override val requestId: String?,
    ) : RemoteCommand
}
