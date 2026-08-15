package com.fumakillers.fireremoteserver

import android.app.Application
import com.fumakillers.fireremoteserver.logging.RemoteLogger

class FireRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RemoteLogger.initialize(this)
        RemoteLogger.info(TAG, "Application started")
    }

    private companion object {
        const val TAG = "FireRemoteApplication"
    }
}
