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
        if (intent?.action == "STOP") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            "ACTION_PAUSE" -> ServiceLocator.serviceCommands.tryEmit("PAUSE")
            "ACTION_RESUME" -> ServiceLocator.serviceCommands.tryEmit("RESUME")
            "ACTION_END" -> ServiceLocator.serviceCommands.tryEmit("END")
        }

        val title = intent?.getStringExtra("TITLE") ?: "Timenote Active"
        val timeText = intent?.getStringExtra("TIME") ?: "00:00:00"
        val baseMillis = intent?.getLongExtra("BASE_MILLIS", System.currentTimeMillis()) ?: System.currentTimeMillis()
        val isPaused = intent?.getBooleanExtra("IS_PAUSED", false) ?: false

        createNotificationChannel()

        // 1. FIX APP RESUME INTENT
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            // Adding NEW_TASK guarantees the app opens correctly even if swiped away!
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeIntent = Intent(this, TimerForegroundService::class.java).apply {
            action = if (isPaused) "ACTION_RESUME" else "ACTION_PAUSE"
        }
        val pauseResumePending = PendingIntent.getService(this, 1, pauseResumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pauseResumeAction = NotificationCompat.Action(
            if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            if (isPaused) "Resume" else "Pause", pauseResumePending
        )

        val endIntent = Intent(this, TimerForegroundService::class.java).apply { action = "ACTION_END" }
        val endPending = PendingIntent.getService(this, 2, endIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val endAction = NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "End", endPending)

        // 2. THE CHRONOMETER MAGIC
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(endAction)

        // If running, OS handles ticking. If paused, we show the static text.
        if (!isPaused) {
            builder.setUsesChronometer(true)
            builder.setWhen(baseMillis) // Starts counting natively from this exact time
        } else {
            builder.setUsesChronometer(false)
            builder.setContentText(timeText)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, builder.build())
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