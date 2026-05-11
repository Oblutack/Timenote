package com.oblutack.timenote.feature_timer.domain

import kotlinx.coroutines.flow.MutableSharedFlow

interface TimerServiceManager {
    fun startService()
    fun stopService()
    // NEW: Added baseMillis so the OS can tick the timer natively
    fun updateNotification(title: String, timeText: String, baseMillis: Long, isPaused: Boolean)
}

object ServiceLocator {
    var timerServiceManager: TimerServiceManager? = null
    val serviceCommands = MutableSharedFlow<String>(extraBufferCapacity = 1)
}