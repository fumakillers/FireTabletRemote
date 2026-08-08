package com.fumakillers.fireremoteserver.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FireRemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceBridge.connect(this)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityServiceBridge.disconnect(this)
        Log.i(TAG, "Accessibility service disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityServiceBridge.disconnect(this)
        Log.i(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FireRemoteAccessibility"
    }
}
