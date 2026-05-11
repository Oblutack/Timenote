package com.oblutack.timenote.feature_timer.domain

import kotlinx.coroutines.flow.MutableSharedFlow

interface TimerServiceManager {
    fun startService()
    fun stopService()
    fun updateNotification(title: String, time: String, isPaused: Boolean)
}

object ServiceLocator {
    var timerServiceManager: TimerServiceManager? = null
    val serviceCommands = MutableSharedFlow<String>(extraBufferCapacity = 1)
}