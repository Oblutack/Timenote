package com.oblutack.timenote.core

/**
 * Absolute-timestamp bookkeeping for one timer session.
 *
 * Time is always derived from wall-clock timestamps passed in as `now`, never from counting ticks,
 * so it stays correct while the app is backgrounded or killed and restored. All functions take
 * `now` explicitly to keep this class free of platform clocks and easy to test.
 */
class SessionClock {
    var startMillis = 0L
        private set
    var totalPauseMillis = 0L
        private set
    var pauseStartMillis = 0L
        private set
    var isPaused = false
        private set

    fun start(now: Long) {
        startMillis = now
        totalPauseMillis = 0L
        pauseStartMillis = 0L
        isPaused = false
    }

    /** Restores a session saved by the backup (e.g. after the app was swiped away). */
    fun restore(startMillis: Long, totalPauseMillis: Long, pauseStartMillis: Long?, isPaused: Boolean) {
        this.startMillis = startMillis
        this.totalPauseMillis = totalPauseMillis
        this.pauseStartMillis = pauseStartMillis ?: 0L
        this.isPaused = isPaused
    }

    fun pause(now: Long) {
        pauseStartMillis = now
        isPaused = true
    }

    /** Ends the current pause and returns how long it lasted. */
    fun resume(now: Long): Long {
        val pauseDuration = now - pauseStartMillis
        totalPauseMillis += pauseDuration
        pauseStartMillis = 0L
        isPaused = false
        return pauseDuration
    }

    /** Length of the pause in progress, 0 when running. */
    fun currentPauseMillis(now: Long): Long = if (isPaused) now - pauseStartMillis else 0L

    /** Time spent working: everything except pauses. */
    fun activeMillis(now: Long): Long = now - startMillis - totalPauseMillis - currentPauseMillis(now)

    /** The big clock on screen: total time since start, frozen at the moment of pausing. */
    fun displayElapsedMillis(now: Long): Long = if (isPaused) pauseStartMillis - startMillis else now - startMillis

    fun activeSeconds(now: Long): Int = (activeMillis(now) / 1000).toInt()

    fun pauseSeconds(now: Long): Int = (totalPauseMillis / 1000).toInt() + (currentPauseMillis(now) / 1000).toInt()

    /** Active plus pause time, used for timeline timestamps and the saved duration. */
    fun elapsedSeconds(now: Long): Int = activeSeconds(now) + pauseSeconds(now)
}
