package com.oblutack.timenote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.oblutack.timenote.feature_timer.domain.ServiceLocator

class TimerForegroundService : Service() {
    private val CHANNEL_ID = "TimerServiceChannel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. HANDLE INCOMING BUTTON CLICKS FROM THE NOTIFICATION
        when (intent?.action) {
            "STOP" -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            "ACTION_PAUSE" -> ServiceLocator.serviceCommands.tryEmit("PAUSE")
            "ACTION_RESUME" -> ServiceLocator.serviceCommands.tryEmit("RESUME")
            "ACTION_END" -> ServiceLocator.serviceCommands.tryEmit("END")
        }

        // 2. BUILD THE UI
        val title = intent?.getStringExtra("TITLE") ?: "Timenote Active"
        val time = intent?.getStringExtra("TIME") ?: "00:00:00"
        val isPaused = intent?.getBooleanExtra("IS_PAUSED", false) ?: false

        createNotificationChannel()

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. CREATE THE ACTION BUTTONS
        val pauseResumeIntent = Intent(this, TimerForegroundService::class.java).apply {
            action = if (isPaused) "ACTION_RESUME" else "ACTION_PAUSE"
        }
        val pauseResumePending = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseResumeAction = NotificationCompat.Action(
            if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            if (isPaused) "Resume" else "Pause",
            pauseResumePending
        )

        val endIntent = Intent(this, TimerForegroundService::class.java).apply { action = "ACTION_END" }
        val endPending = PendingIntent.getService(
            this, 2, endIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val endAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "End",
            endPending
        )

        // 4. ASSEMBLE NOTIFICATION
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(time)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOngoing(!isPaused) // Allow user to swipe away IF paused
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction) // Inject buttons
            .addAction(endAction)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Premium Timer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}