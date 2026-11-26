package com.oblutack.timenote.feature_timer.domain

interface TimerServiceManager {
    fun startService()
    fun stopService()
    // baseMillis lets the OS tick the notification chronometer natively
    fun updateNotification(title: String, timeText: String, baseMillis: Long, isPaused: Boolean)

    /** Asks, [delayMillis] from now, whether the person is still working (replaces an earlier question). */
    fun scheduleForgottenCheck(delayMillis: Long)

    /** The session is over: no question is due any more, and one that is already showing is taken away. */
    fun cancelForgottenCheck()
}

/** Actions the user can trigger from the timer notification buttons. */
enum class TimerServiceCommand { PAUSE, RESUME, END }
