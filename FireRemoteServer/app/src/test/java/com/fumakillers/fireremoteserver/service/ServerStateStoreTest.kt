package com.fumakillers.fireremoteserver.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerStateStoreTest {
    @Test
    fun tracksTransitionsAndNotifiesOnlyWhenStateChanges() {
        val store = ServerStateStore()
        val observed = mutableListOf<ServerState>()
        val listener: (ServerState) -> Unit = { observed.add(it) }
        store.addListener(listener)

        store.update(ServerState.STARTING)
        store.update(ServerState.RUNNING)
        store.update(ServerState.RUNNING)

        assertEquals(ServerState.RUNNING, store.current)
        assertEquals(listOf(ServerState.STARTING, ServerState.RUNNING), observed)

        store.removeListener(listener)
        store.update(ServerState.STOPPED)
        assertEquals(listOf(ServerState.STARTING, ServerState.RUNNING), observed)
    }
}
