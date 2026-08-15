package com.fumakillers.fireremoteserver.service

import android.content.Context

class AndroidServerRecoveryStorage(context: Context) : ServerRecoveryStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): ServerRecoverySnapshot {
        val desiredState = runCatching {
            ServerDesiredState.valueOf(
                preferences.getString(KEY_DESIRED_STATE, null)
                    ?: ServerDesiredState.STOPPED.name,
            )
        }.getOrDefault(ServerDesiredState.STOPPED)
        return ServerRecoverySnapshot(
            desiredState = desiredState,
            failureWindowStartedAtMs = preferences.getLong(KEY_FAILURE_WINDOW_START, 0),
            failureCount = preferences.getInt(KEY_FAILURE_COUNT, 0),
            restartBlocked = preferences.getBoolean(KEY_RESTART_BLOCKED, false),
        )
    }

    override fun save(snapshot: ServerRecoverySnapshot) {
        preferences.edit()
            .putString(KEY_DESIRED_STATE, snapshot.desiredState.name)
            .putLong(KEY_FAILURE_WINDOW_START, snapshot.failureWindowStartedAtMs)
            .putInt(KEY_FAILURE_COUNT, snapshot.failureCount)
            .putBoolean(KEY_RESTART_BLOCKED, snapshot.restartBlocked)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "server_recovery"
        const val KEY_DESIRED_STATE = "desired_state"
        const val KEY_FAILURE_WINDOW_START = "failure_window_start"
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_RESTART_BLOCKED = "restart_blocked"
    }
}

fun Context.createServerRecoveryPolicy() = ServerRecoveryPolicy(
    AndroidServerRecoveryStorage(this),
)
