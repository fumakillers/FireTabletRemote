package com.fumakillers.fireremoteserver.service

import java.util.concurrent.CopyOnWriteArraySet

enum class ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

class ServerStateStore(initialState: ServerState = ServerState.STOPPED) {
    private val listeners = CopyOnWriteArraySet<(ServerState) -> Unit>()

    @Volatile
    var current: ServerState = initialState
        private set

    @Synchronized
    fun update(state: ServerState) {
        if (current == state) return
        current = state
        listeners.forEach { it(state) }
    }

    fun addListener(listener: (ServerState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ServerState) -> Unit) {
        listeners.remove(listener)
    }
}

object ServerRuntimeState {
    val store = ServerStateStore()
}
