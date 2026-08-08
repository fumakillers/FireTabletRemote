package com.fumakillers.fireremoteserver.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.fumakillers.fireremoteserver.command.AndroidActionGateway
import com.fumakillers.fireremoteserver.command.AndroidActionResult

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
    override fun performBack(): AndroidActionResult {
        Log.i(TAG, "Back command received")
        val connectedService = service
        if (connectedService == null) {
            Log.w(TAG, "Back failed: Accessibility service is not connected")
            return AndroidActionResult.ServiceNotConnected
        }

        return try {
            if (connectedService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                Log.i(TAG, "Back action performed successfully")
                AndroidActionResult.Performed
            } else {
                Log.w(TAG, "Back action was rejected by AccessibilityService")
                AndroidActionResult.Rejected
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Back action failed", error)
            AndroidActionResult.Failed
        }
    }

    private const val TAG = "FireRemoteCommand"
}
