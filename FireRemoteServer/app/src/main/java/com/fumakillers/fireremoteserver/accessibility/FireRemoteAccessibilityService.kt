package com.fumakillers.fireremoteserver.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FireRemoteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    private companion object {
        const val TAG = "FireRemoteAccessibility"
    }
}
