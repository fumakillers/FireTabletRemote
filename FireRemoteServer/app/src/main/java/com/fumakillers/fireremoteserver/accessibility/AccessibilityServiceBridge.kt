package com.fumakillers.fireremoteserver.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.fumakillers.fireremoteserver.command.AndroidActionGateway
import com.fumakillers.fireremoteserver.command.AndroidActionResult
import com.fumakillers.fireremoteserver.command.AndroidGlobalAction

object AccessibilityServiceBridge : AndroidActionGateway {
    private var service: FireRemoteAccessibilityService? = null

    @Synchronized
    fun connect(connectedService: FireRemoteAccessibilityService) {
        service = connectedService
    }

    @Synchronized
    fun disconnect(disconnectedService: FireRemoteAccessibilityService) {
        if (service === disconnectedService) {
            service = null
        }
    }

    val isConnected: Boolean
        @Synchronized get() = service != null

    @Synchronized
    override fun perform(action: AndroidGlobalAction): AndroidActionResult {
        val actionName = action.name.lowercase()
        Log.i(TAG, "$actionName command received")
        val connectedService = service
        if (connectedService == null) {
            Log.w(TAG, "$actionName failed: Accessibility service is not connected")
            return AndroidActionResult.ServiceNotConnected
        }

        val globalAction = when (action) {
            AndroidGlobalAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            AndroidGlobalAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            AndroidGlobalAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        }

        return try {
            if (connectedService.performGlobalAction(globalAction)) {
                Log.i(TAG, "$actionName action performed successfully")
                AndroidActionResult.Performed
            } else {
                Log.w(TAG, "$actionName action was rejected by AccessibilityService")
                AndroidActionResult.Rejected
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "$actionName action failed", error)
            AndroidActionResult.Failed
        }
    }

    private const val TAG = "FireRemoteCommand"
}
