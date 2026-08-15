package com.fumakillers.fireremoteserver.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.fumakillers.fireremoteserver.logging.RemoteLogger

class FireRemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceBridge.connect(this)
        AccessibilityScreenshotGateway.connect(this)
        RemoteLogger.info(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        RemoteLogger.warn(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityServiceBridge.disconnect(this)
        AccessibilityScreenshotGateway.disconnect(this)
        RemoteLogger.info(TAG, "Accessibility service disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityServiceBridge.disconnect(this)
        AccessibilityScreenshotGateway.disconnect(this)
        RemoteLogger.info(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FireRemoteAccessibility"
    }
}
