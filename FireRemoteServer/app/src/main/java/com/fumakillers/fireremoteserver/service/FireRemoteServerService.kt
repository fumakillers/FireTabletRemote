package com.fumakillers.fireremoteserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.fumakillers.fireremoteserver.MainActivity
import com.fumakillers.fireremoteserver.accessibility.AccessibilityServiceBridge
import com.fumakillers.fireremoteserver.accessibility.AccessibilityScreenshotGateway
import com.fumakillers.fireremoteserver.command.AndroidCommandExecutor
import com.fumakillers.fireremoteserver.command.CommandDispatcher
import com.fumakillers.fireremoteserver.diagnostics.MemoryDiagnostics
import com.fumakillers.fireremoteserver.logging.RemoteLogger
import com.fumakillers.fireremoteserver.network.CommandWebSocketServer
import com.fumakillers.fireremoteserver.preview.AccessibilityScreenshotProvider
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class FireRemoteServerService : Service() {
    private var server: CommandWebSocketServer? = null
    private var previewProvider: AccessibilityScreenshotProvider? = null
    private var memoryScheduler: ScheduledExecutorService? = null
    private lateinit var recoveryPolicy: ServerRecoveryPolicy
    private val restartHandler = Handler(Looper.getMainLooper())
    private var retryScheduled = false
    private var preserveErrorState = false
    private var serverGeneration = 0L

    @Volatile
    private var destroying = false

    override fun onCreate() {
        super.onCreate()
        recoveryPolicy = createServerRecoveryPolicy()
        RemoteLogger.info(TAG, "Starting service")
        ServerRuntimeState.store.update(ServerState.STARTING)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting on port $DEFAULT_PORT"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!recoveryPolicy.shouldRecover) {
            val desiredState = recoveryPolicy.desiredState
            RemoteLogger.warn(
                TAG,
                "Ignoring service start: desiredState=$desiredState automaticRestartAllowed=false",
            )
            preserveErrorState = desiredState == ServerDesiredState.RUNNING
            ServerRuntimeState.store.update(
                if (preserveErrorState) ServerState.ERROR else ServerState.STOPPED,
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startMemoryDiagnostics()
        startWebSocketServer()
        return START_STICKY
    }

    override fun onDestroy() {
        destroying = true
        serverGeneration++
        restartHandler.removeCallbacksAndMessages(null)
        retryScheduled = false
        memoryScheduler?.shutdownNow()
        memoryScheduler = null
        try {
            server?.stop(1_000)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            RemoteLogger.warn(TAG, "Interrupted while stopping WebSocket server", error)
        } catch (error: RuntimeException) {
            RemoteLogger.error(TAG, "Could not stop WebSocket server cleanly", error)
        } finally {
            server = null
            previewProvider?.close()
            previewProvider = null
            if (!preserveErrorState) ServerRuntimeState.store.update(ServerState.STOPPED)
            RemoteLogger.info(
                TAG,
                "Service stopped: desiredState=${recoveryPolicy.desiredState} " +
                    "preservedError=$preserveErrorState",
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Synchronized
    private fun startWebSocketServer() {
        if (destroying || server != null || !recoveryPolicy.shouldRecover) return
        retryScheduled = false
        preserveErrorState = false
        val generation = ++serverGeneration
        ServerRuntimeState.store.update(ServerState.STARTING)

        val provider = AccessibilityScreenshotProvider(AccessibilityScreenshotGateway)
        val webSocketServer = CommandWebSocketServer(
            DEFAULT_PORT,
            CommandDispatcher(
                AndroidCommandExecutor(AccessibilityServiceBridge, AccessibilityServiceBridge),
                provider,
            ),
            onListening = { handleServerListening(generation) },
            onFatalError = { error ->
                handleServerFailure(generation, "WebSocket server failed", error)
            },
        )
        previewProvider = provider
        server = webSocketServer
        try {
            webSocketServer.start()
        } catch (error: RuntimeException) {
            handleServerFailure(generation, "Could not start WebSocket server", error)
        }
    }

    @Synchronized
    private fun handleServerListening(generation: Long) {
        if (destroying || generation != serverGeneration || !recoveryPolicy.shouldRecover) return
        recoveryPolicy.recordStartSucceeded()
        ServerRuntimeState.store.update(ServerState.RUNNING)
        updateNotification("Listening on port $DEFAULT_PORT")
    }

    @Synchronized
    private fun handleServerFailure(generation: Long, message: String, error: Throwable) {
        if (destroying || generation != serverGeneration) return
        RemoteLogger.error(TAG, message, error)
        serverGeneration++
        server = null
        previewProvider?.close()
        previewProvider = null
        ServerRuntimeState.store.update(ServerState.ERROR)

        val decision = recoveryPolicy.recordStartFailure()
        if (decision.restartBlocked) {
            preserveErrorState = true
            RemoteLogger.error(
                TAG,
                "Automatic server restart blocked after ${decision.failureCount} failures " +
                    "within ${ServerRecoveryPolicy.FAILURE_WINDOW_MS}ms",
            )
            updateNotification("Automatic restart stopped; open app to retry")
            stopSelf()
            return
        }

        if (!retryScheduled) {
            retryScheduled = true
            RemoteLogger.warn(
                TAG,
                "Scheduling server restart after failure ${decision.failureCount}/" +
                    ServerRecoveryPolicy.MAX_FAILURES_IN_WINDOW,
            )
            updateNotification("Restarting server after failure")
            restartHandler.postDelayed(
                { startWebSocketServer() },
                RETRY_DELAY_MS,
            )
        }
    }

    private fun startMemoryDiagnostics() {
        if (memoryScheduler != null) return
        memoryScheduler = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "FireRemoteMemoryMonitor").apply { isDaemon = true }
        }.also { scheduler ->
            scheduler.scheduleWithFixedDelay(
                MemoryDiagnostics::logCurrentUsage,
                MEMORY_LOG_INTERVAL_SECONDS,
                MEMORY_LOG_INTERVAL_SECONDS,
                TimeUnit.SECONDS,
            )
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Remote server",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(status: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Fire Remote Server")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            createNotification(status),
        )
    }

    companion object {
        const val DEFAULT_PORT = 8080
        internal const val RETRY_DELAY_MS = 5_000L
        internal const val MEMORY_LOG_INTERVAL_SECONDS = 60L
        private const val CHANNEL_ID = "fire_remote_server"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "FireRemoteService"
    }
}
