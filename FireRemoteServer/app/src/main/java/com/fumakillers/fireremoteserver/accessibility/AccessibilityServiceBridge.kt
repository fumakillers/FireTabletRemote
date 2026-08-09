package com.fumakillers.fireremoteserver.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.WindowManager
import com.fumakillers.fireremoteserver.command.AndroidActionGateway
import com.fumakillers.fireremoteserver.command.AndroidActionResult
import com.fumakillers.fireremoteserver.command.AndroidGestureGateway
import com.fumakillers.fireremoteserver.command.AndroidGestureResult
import com.fumakillers.fireremoteserver.command.AndroidGlobalAction

object AccessibilityServiceBridge : AndroidActionGateway, AndroidGestureGateway {
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

    override fun performTap(
        x: Int,
        y: Int,
        callback: (AndroidGestureResult) -> Unit,
    ) {
        Log.i(TAG, "tap command received: x=$x y=$y")
        val connectedService = synchronized(this) { service }
        if (connectedService == null) {
            Log.w(TAG, "tap failed: Accessibility service is not connected")
            callback(AndroidGestureResult.ServiceNotConnected)
            return
        }

        try {
            val screenBounds = connectedService
                .getSystemService(WindowManager::class.java)
                .maximumWindowMetrics
                .bounds
            if (x !in 0 until screenBounds.width() || y !in 0 until screenBounds.height()) {
                Log.w(
                    TAG,
                    "tap rejected: coordinates outside ${screenBounds.width()}x${screenBounds.height()}",
                )
                callback(AndroidGestureResult.InvalidCoordinates)
                return
            }

            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
                .build()
            val accepted = connectedService.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        Log.i(TAG, "tap gesture completed: x=$x y=$y")
                        callback(AndroidGestureResult.Completed)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        Log.w(TAG, "tap gesture cancelled: x=$x y=$y")
                        callback(AndroidGestureResult.Cancelled)
                    }
                },
                null,
            )
            if (!accepted) {
                Log.w(TAG, "tap gesture was rejected by AccessibilityService")
                callback(AndroidGestureResult.Rejected)
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "tap gesture failed", error)
            callback(AndroidGestureResult.Failed)
        }
    }

    private const val TAP_DURATION_MS = 75L
    private const val TAG = "FireRemoteCommand"
}
