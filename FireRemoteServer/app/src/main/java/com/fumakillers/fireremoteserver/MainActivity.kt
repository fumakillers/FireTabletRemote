package com.fumakillers.fireremoteserver

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.fumakillers.fireremoteserver.accessibility.AccessibilityServiceBridge
import com.fumakillers.fireremoteserver.service.FireRemoteServerService

class MainActivity : Activity() {
    private lateinit var accessibilityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(context).apply {
                text = "Fire Remote Server\nWebSocket: ws://<tablet-ip>:${FireRemoteServerService.DEFAULT_PORT}"
                textSize = 22f
            })
            accessibilityStatus = TextView(context)
            addView(accessibilityStatus)
            addView(Button(context).apply {
                text = "Start server"
                setOnClickListener {
                    startForegroundService(Intent(context, FireRemoteServerService::class.java))
                }
            }, matchWidth())
            addView(Button(context).apply {
                text = "Stop server"
                setOnClickListener {
                    stopService(Intent(context, FireRemoteServerService::class.java))
                }
            }, matchWidth())
            addView(Button(context).apply {
                text = "Open Accessibility settings"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }, matchWidth())
            addView(TextView(context).apply {
                text = "Received commands are logged with tags FireRemoteWebSocket and FireRemoteCommand. Tap and long-press gestures are not implemented yet."
            })
        }
        setContentView(content)
        updateAccessibilityStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::accessibilityStatus.isInitialized) {
            updateAccessibilityStatus()
        }
    }

    private fun updateAccessibilityStatus() {
        accessibilityStatus.text = if (AccessibilityServiceBridge.isConnected) {
            "Accessibility: Connected"
        } else {
            "Accessibility: Not connected"
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
