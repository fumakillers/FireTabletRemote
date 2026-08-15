package com.fumakillers.fireremoteserver

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.fumakillers.fireremoteserver.accessibility.AccessibilityServiceBridge
import com.fumakillers.fireremoteserver.logging.RecentLogRecord
import com.fumakillers.fireremoteserver.logging.RemoteLogger
import com.fumakillers.fireremoteserver.network.DeviceIpAddressResolver
import com.fumakillers.fireremoteserver.service.FireRemoteServerService
import com.fumakillers.fireremoteserver.service.ServerRuntimeState
import com.fumakillers.fireremoteserver.service.ServerState
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {
    private lateinit var endpointText: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var serverStatus: TextView
    private lateinit var ipAddressStatus: TextView
    private lateinit var startServerButton: Button
    private lateinit var stopServerButton: Button
    private lateinit var logScrollView: ScrollView
    private lateinit var logTextView: TextView

    private val addressExecutor = Executors.newSingleThreadExecutor()
    private val addressRequest = AtomicInteger()
    private val displayedLogs = ArrayDeque<RecentLogRecord>()
    private var displayedLineCount = 0
    private var clearedThroughId = 0L
    private var logSubscribed = false

    private val serverStateListener: (ServerState) -> Unit = { state ->
        runOnUiThread { updateServerStatus(state) }
    }
    private val recentLogListener: (RecentLogRecord) -> Unit = { record ->
        runOnUiThread {
            if (logSubscribed && record.id > clearedThroughId) appendLogRecord(record)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)

            endpointText = TextView(context).apply { textSize = 22f }
            addView(endpointText)
            accessibilityStatus = TextView(context)
            addView(accessibilityStatus)
            serverStatus = TextView(context)
            addView(serverStatus)
            ipAddressStatus = TextView(context)
            addView(ipAddressStatus)

            addView(createServerButtonRow(), matchWidth())
            addView(createLogHeader(), matchWidth())

            logTextView = TextView(context).apply {
                setTextIsSelectable(true)
                typeface = Typeface.MONOSPACE
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(dp(8), dp(6), dp(8), dp(6))
            }
            logScrollView = ScrollView(context).apply {
                isFillViewport = true
                setBackgroundColor(Color.rgb(32, 32, 32))
                addView(
                    logTextView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            addView(
                logScrollView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(content)
        updateAccessibilityStatus()
        updateServerStatus(ServerRuntimeState.store.current)
        updateIpAddress()
    }

    override fun onStart() {
        super.onStart()
        ServerRuntimeState.store.addListener(serverStateListener)
        updateServerStatus(ServerRuntimeState.store.current)
        logSubscribed = true
        showRecentLogs(
            RemoteLogger.subscribe(recentLogListener).filter { it.id > clearedThroughId },
        )
    }

    override fun onStop() {
        logSubscribed = false
        RemoteLogger.removeListener(recentLogListener)
        ServerRuntimeState.store.removeListener(serverStateListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::accessibilityStatus.isInitialized) updateAccessibilityStatus()
        if (::serverStatus.isInitialized) updateServerStatus(ServerRuntimeState.store.current)
        if (::ipAddressStatus.isInitialized) updateIpAddress()
    }

    override fun onDestroy() {
        addressRequest.incrementAndGet()
        addressExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createServerButtonRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        startServerButton = Button(context).apply {
            text = "Start server"
            setOnClickListener {
                try {
                    startForegroundService(Intent(context, FireRemoteServerService::class.java))
                } catch (error: RuntimeException) {
                    RemoteLogger.error(TAG, "Could not request server start", error)
                    ServerRuntimeState.store.update(ServerState.ERROR)
                }
            }
        }
        addView(startServerButton, weightedWidth())
        stopServerButton = Button(context).apply {
            text = "Stop server"
            setOnClickListener {
                val stopped = stopService(Intent(context, FireRemoteServerService::class.java))
                if (!stopped) ServerRuntimeState.store.update(ServerState.STOPPED)
            }
        }
        addView(stopServerButton, weightedWidth())
        addView(Button(context).apply {
            text = "Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, weightedWidth())
    }

    private fun createLogHeader() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = "Recent Log"
            textSize = 16f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(context).apply {
            text = "CLEAR"
            setOnClickListener { clearDisplayedLogs() }
        })
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

    private fun updateIpAddress() {
        val request = addressRequest.incrementAndGet()
        ipAddressStatus.text = "IP Address: Checking..."
        addressExecutor.execute {
            val address = try {
                DeviceIpAddressResolver.resolve(applicationContext)
            } catch (error: Exception) {
                RemoteLogger.warn(TAG, "Could not determine device IP address", error)
                null
            }
            runOnUiThread {
                if (request != addressRequest.get() || isDestroyed) return@runOnUiThread
                ipAddressStatus.text = "IP Address: ${address ?: "Unavailable"}"
                endpointText.text = if (address == null) {
                    "Fire Remote Server\nWebSocket: Unavailable"
                } else {
                    "Fire Remote Server\nWebSocket: ws://$address:${FireRemoteServerService.DEFAULT_PORT}"
                }
            }
        }
    }

    private fun showRecentLogs(records: List<RecentLogRecord>) {
        displayedLogs.clear()
        displayedLineCount = 0
        records.forEach(::addToDisplayedBuffer)
        logTextView.text = displayedLogs.joinToString("\n") { it.displayText }
        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun appendLogRecord(record: RecentLogRecord) {
        val wasAtBottom = logScrollView.scrollY + logScrollView.height >=
            logTextView.height - dp(24)
        val trimmed = addToDisplayedBuffer(record)
        if (trimmed) {
            logTextView.text = displayedLogs.joinToString("\n") { it.displayText }
        } else {
            if (logTextView.length() > 0) logTextView.append("\n")
            logTextView.append(record.displayText)
        }
        if (wasAtBottom) logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun addToDisplayedBuffer(record: RecentLogRecord): Boolean {
        displayedLogs.addLast(record)
        displayedLineCount += record.lineCount
        var trimmed = false
        while (displayedLineCount > MAX_DISPLAY_LINES && displayedLogs.size > 1) {
            displayedLineCount -= displayedLogs.removeFirst().lineCount
            trimmed = true
        }
        return trimmed
    }

    private fun clearDisplayedLogs() {
        clearedThroughId = RemoteLogger.latestRecordId()
        displayedLogs.clear()
        displayedLineCount = 0
        logTextView.text = ""
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun weightedWidth() = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f,
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "FireRemoteActivity"
        const val MAX_DISPLAY_LINES = 500
    }
}
