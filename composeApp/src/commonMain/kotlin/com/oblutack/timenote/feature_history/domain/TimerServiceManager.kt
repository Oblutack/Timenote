package com.oblutack.timenote.feature_timer.domain

// 1. The Interface our ViewModel will use
interface TimerServiceManager {
    fun startService()
    fun stopService()
    fun updateNotification(title: String, time: String)
}

// 2. A simple global locator so we don't have to rewrite your Navigation/ViewModel setup
object ServiceLocator {
    var timerServiceManager: TimerServiceManager? = null
}