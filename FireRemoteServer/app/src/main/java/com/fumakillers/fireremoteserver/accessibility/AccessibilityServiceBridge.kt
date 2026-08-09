package com.fumakillers.fireremoteserver.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.WindowManager
import com.fumakillers.fireremoteserver.command.AndroidActionGateway
import com.fumakillers.fireremoteserver.command.AndroidActionResult
import com.fumakillers.fireremoteserver.command.AndroidGesture
import com.fumakillers.fireremoteserver.command.AndroidGestureGateway
import com.fumakillers.fireremoteserver.command.AndroidGestureResult
import com.fumakillers.fireremoteserver.command.AndroidGlobalAction
import java.util.concurrent.atomic.AtomicBoolean

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

    override fun perform(
        gesture: AndroidGesture,
        callback: (AndroidGestureResult) -> Unit,
    ) {
        val gestureName = when (gesture) {
            is AndroidGesture.Tap -> "tap"
            is AndroidGesture.LongPress -> "longPress"
            is AndroidGesture.Swipe -> "swipe"
        }
        Log.i(TAG, "$gestureName command received: $gesture")
        val callbackCompleted = AtomicBoolean(false)
        fun complete(result: AndroidGestureResult) {
            if (callbackCompleted.compareAndSet(false, true)) {
                callback(result)
            }
        }
        val connectedService = synchronized(this) { service }
        if (connectedService == null) {
            Log.w(TAG, "$gestureName failed: Accessibility service is not connected")
            complete(AndroidGestureResult.ServiceNotConnected)
            return
        }

        try {
            val screenBounds = connectedService
                .getSystemService(WindowManager::class.java)
                .maximumWindowMetrics
                .bounds
            val points = when (gesture) {
                is AndroidGesture.Tap -> listOf(gesture.x to gesture.y)
                is AndroidGesture.LongPress -> listOf(gesture.x to gesture.y)
                is AndroidGesture.Swipe -> listOf(
                    gesture.startX to gesture.startY,
                    gesture.endX to gesture.endY,
                )
            }
            if (points.any { (x, y) ->
                    x !in 0 until screenBounds.width() || y !in 0 until screenBounds.height()
                }
            ) {
                Log.w(
                    TAG,
                    "$gestureName rejected: coordinates outside " +
                        "${screenBounds.width()}x${screenBounds.height()}",
                )
                complete(AndroidGestureResult.InvalidCoordinates)
                return
            }

            val path = Path().apply {
                when (gesture) {
                    is AndroidGesture.Tap -> moveTo(
                        gesture.x.toFloat(),
                        gesture.y.toFloat(),
                    )
                    is AndroidGesture.LongPress -> moveTo(
                        gesture.x.toFloat(),
                        gesture.y.toFloat(),
                    )
                    is AndroidGesture.Swipe -> {
                        moveTo(gesture.startX.toFloat(), gesture.startY.toFloat())
                        lineTo(gesture.endX.toFloat(), gesture.endY.toFloat())
                    }
                }
            }
            val gestureDescription = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(path, 0, gesture.durationMs),
                )
                .build()
            val accepted = connectedService.dispatchGesture(
                gestureDescription,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        Log.i(TAG, "$gestureName gesture completed")
                        complete(AndroidGestureResult.Completed)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        Log.w(TAG, "$gestureName gesture cancelled")
                        complete(AndroidGestureResult.Cancelled)
                    }
                },
                null,
            )
            if (!accepted) {
                Log.w(TAG, "$gestureName gesture was rejected by AccessibilityService")
                complete(AndroidGestureResult.Rejected)
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "$gestureName gesture failed", error)
            complete(AndroidGestureResult.Failed)
        }
    }

    private const val TAG = "FireRemoteCommand"
}
