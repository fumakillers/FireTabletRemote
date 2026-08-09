package com.fumakillers.fireremoteserver

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.fumakillers.fireremoteserver.accessibility.AccessibilityServiceBridge
import com.fumakillers.fireremoteserver.service.FireRemoteServerService
import com.fumakillers.fireremoteserver.service.ServerRuntimeState
import com.fumakillers.fireremoteserver.service.ServerState

class MainActivity : Activity() {
    private lateinit var accessibilityStatus: TextView
    private lateinit var serverStatus: TextView
    private lateinit var startServerButton: Button
    private lateinit var stopServerButton: Button
    private val serverStateListener: (ServerState) -> Unit = { state ->
        runOnUiThread { updateServerStatus(state) }
    }

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
            serverStatus = TextView(context)
            addView(serverStatus)
            startServerButton = Button(context).apply {
                text = "Start server"
                setOnClickListener {
                    try {
                        startForegroundService(Intent(context, FireRemoteServerService::class.java))
                    } catch (error: RuntimeException) {
                        Log.e(TAG, "Could not request server start", error)
                        ServerRuntimeState.store.update(ServerState.ERROR)
                    }
                }
            }
            addView(startServerButton, matchWidth())
            stopServerButton = Button(context).apply {
                text = "Stop server"
                setOnClickListener {
                    val stopped = stopService(Intent(context, FireRemoteServerService::class.java))
                    if (!stopped) {
                        ServerRuntimeState.store.update(ServerState.STOPPED)
                    }
                }
            }
            addView(stopServerButton, matchWidth())
            addView(Button(context).apply {
                text = "Open Accessibility settings"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }, matchWidth())
            addView(TextView(context).apply {
                text = "Received commands and gesture results are logged with tags FireRemoteWebSocket and FireRemoteCommand."
            })
        }
        setContentView(content)
        updateAccessibilityStatus()
        updateServerStatus(ServerRuntimeState.store.current)
    }

    override fun onStart() {
        super.onStart()
        ServerRuntimeState.store.addListener(serverStateListener)
        updateServerStatus(ServerRuntimeState.store.current)
    }

    override fun onStop() {
        ServerRuntimeState.store.removeListener(serverStateListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::accessibilityStatus.isInitialized) {
            updateAccessibilityStatus()
        }
        if (::serverStatus.isInitialized) {
            updateServerStatus(ServerRuntimeState.store.current)
        }
    }

    private fun updateAccessibilityStatus() {
        accessibilityStatus.text = if (AccessibilityServiceBridge.isConnected) {
            "Accessibility: Connected"
        } else {
            "Accessibility: Not connected"
        }
    }

    private fun updateServerStatus(state: ServerState) {
        serverStatus.text = when (state) {
            ServerState.STOPPED -> "Server: Stopped"
            ServerState.STARTING -> "Server: Starting"
            ServerState.RUNNING -> "Server: Running"
            ServerState.ERROR -> "Server: Error"
        }
        startServerButton.isEnabled = state == ServerState.STOPPED
        stopServerButton.isEnabled = state != ServerState.STOPPED
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private companion object {
        const val TAG = "FireRemoteActivity"
    }
}
