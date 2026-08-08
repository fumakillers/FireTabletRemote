package com.fumakillers.fireremoteserver.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.fumakillers.fireremoteserver.MainActivity
import com.fumakillers.fireremoteserver.command.AndroidCommandExecutor
import com.fumakillers.fireremoteserver.command.CommandDispatcher
import com.fumakillers.fireremoteserver.network.CommandWebSocketServer

class FireRemoteServerService : Service() {
    private var server: CommandWebSocketServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Fire Remote Server")
            .setContentText("Listening on port $DEFAULT_PORT")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(openApp)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        server = CommandWebSocketServer(
            DEFAULT_PORT,
            CommandDispatcher(AndroidCommandExecutor()),
        ).also { it.start() }
    }

    override fun onDestroy() {
        try {
            server?.stop(1_000)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Interrupted while stopping WebSocket server", error)
        } finally {
            server = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Remote server",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val DEFAULT_PORT = 8080
        private const val CHANNEL_ID = "fire_remote_server"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "FireRemoteService"
    }
}
