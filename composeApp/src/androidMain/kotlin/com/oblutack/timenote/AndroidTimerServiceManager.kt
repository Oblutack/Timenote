package com.oblutack.timenote

import android.content.Context
import android.content.Intent
import android.os.Build
import com.oblutack.timenote.feature_timer.domain.TimerServiceManager

class AndroidTimerServiceManager(private val context: Context) : TimerServiceManager {
    override fun startService() {
        try {
            val intent = Intent(context, TimerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun stopService() {
        try {
            val intent = Intent(context, TimerForegroundService::class.java).apply { action = "STOP" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun updateNotification(title: String, timeText: String, baseMillis: Long, isPaused: Boolean) {
        try {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                putExtra("TITLE", title)
                putExtra("TIME", timeText)
                putExtra("BASE_MILLIS", baseMillis)
                putExtra("IS_PAUSED", isPaused)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}