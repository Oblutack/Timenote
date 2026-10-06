package com.oblutack.timenote.feature_timer.domain

interface TimerServiceManager {
    fun startService()
    fun stopService()
    // baseMillis lets the OS tick the notification chronometer natively
    fun updateNotification(title: String, timeText: String, baseMillis: Long, isPaused: Boolean)
}

/** Actions the user can trigger from the timer notification buttons. */
enum class TimerServiceCommand { PAUSE, RESUME, END }
