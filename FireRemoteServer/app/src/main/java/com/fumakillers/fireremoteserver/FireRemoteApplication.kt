package com.fumakillers.fireremoteserver

import android.app.Application
import android.content.Intent
import com.fumakillers.fireremoteserver.diagnostics.ProcessExitDiagnostics
import com.fumakillers.fireremoteserver.logging.RemoteLogger
import com.fumakillers.fireremoteserver.service.FireRemoteServerService
import com.fumakillers.fireremoteserver.service.createServerRecoveryPolicy

class FireRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RemoteLogger.initialize(this)
        RemoteLogger.info(TAG, "Application started")
        ProcessExitDiagnostics.logPreviousExit(this)

        val recoveryPolicy = createServerRecoveryPolicy()
        if (recoveryPolicy.shouldRecover) {
            RemoteLogger.info(TAG, "Restarting desired server after process recreation")
            try {
                startForegroundService(Intent(this, FireRemoteServerService::class.java))
            } catch (error: RuntimeException) {
                val decision = recoveryPolicy.recordStartFailure()
                RemoteLogger.error(
                    TAG,
                    "Could not restore desired server: failureCount=${decision.failureCount} " +
                        "restartBlocked=${decision.restartBlocked}",
                    error,
                )
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        RemoteLogger.warn(TAG, "onTrimMemory level=$level")
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        RemoteLogger.warn(TAG, "onLowMemory")
        super.onLowMemory()
    }

    private companion object {
        const val TAG = "FireRemoteApplication"
    }
}
