package com.fumakillers.fireremoteserver.service

enum class ServerDesiredState {
    RUNNING,
    STOPPED,
}

data class ServerRecoverySnapshot(
    val desiredState: ServerDesiredState = ServerDesiredState.STOPPED,
    val failureWindowStartedAtMs: Long = 0,
    val failureCount: Int = 0,
    val restartBlocked: Boolean = false,
)

interface ServerRecoveryStorage {
    fun load(): ServerRecoverySnapshot
    fun save(snapshot: ServerRecoverySnapshot)
}

data class StartFailureDecision(
    val failureCount: Int,
    val restartBlocked: Boolean,
)

class ServerRecoveryPolicy(
    private val storage: ServerRecoveryStorage,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    val desiredState: ServerDesiredState
        @Synchronized get() = storage.load().desiredState

    val shouldRecover: Boolean
        @Synchronized get() = storage.load().let {
            it.desiredState == ServerDesiredState.RUNNING && !it.restartBlocked
        }

    @Synchronized
    fun userRequestedStart() {
        storage.save(
            ServerRecoverySnapshot(desiredState = ServerDesiredState.RUNNING),
        )
    }

    @Synchronized
    fun userRequestedStop() {
        storage.save(
            ServerRecoverySnapshot(desiredState = ServerDesiredState.STOPPED),
        )
    }

    @Synchronized
    fun recordStartSucceeded() {
        val current = storage.load()
        storage.save(
            current.copy(
                failureWindowStartedAtMs = 0,
                failureCount = 0,
                restartBlocked = false,
            ),
        )
    }

    @Synchronized
    fun recordStartFailure(): StartFailureDecision {
        val current = storage.load()
        val now = nowMs()
        val withinWindow = current.failureWindowStartedAtMs > 0 &&
            now - current.failureWindowStartedAtMs in 0..FAILURE_WINDOW_MS
        val count = if (withinWindow) current.failureCount + 1 else 1
        val blocked = count >= MAX_FAILURES_IN_WINDOW
        storage.save(
            current.copy(
                failureWindowStartedAtMs = if (withinWindow) {
                    current.failureWindowStartedAtMs
                } else {
                    now
                },
                failureCount = count,
                restartBlocked = blocked,
            ),
        )
        return StartFailureDecision(count, blocked)
    }

    companion object {
        const val MAX_FAILURES_IN_WINDOW = 3
        const val FAILURE_WINDOW_MS = 5 * 60 * 1_000L
    }
}
