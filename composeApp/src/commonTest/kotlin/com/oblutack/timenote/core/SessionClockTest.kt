package com.oblutack.timenote.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionClockTest {
    private val t0 = 1_000_000L
    private fun running() = SessionClock().apply { start(t0) }

    @Test fun runningSessionCountsAllTimeAsActive() {
        val c = running()
        assertEquals(90, c.activeSeconds(t0 + 90_000))
        assertEquals(0, c.pauseSeconds(t0 + 90_000))
        assertEquals(90_000, c.displayElapsedMillis(t0 + 90_000))
    }

    @Test fun pausedTimeIsExcludedFromActive() {
        val c = running()
        c.pause(t0 + 60_000)              // worked 60s
        val now = t0 + 100_000            // paused for 40s
        assertTrue(c.isPaused)
        assertEquals(40_000, c.currentPauseMillis(now))
        assertEquals(60, c.activeSeconds(now))
        assertEquals(40, c.pauseSeconds(now))
        assertEquals(100, c.elapsedSeconds(now))
    }

    @Test fun displayClockFreezesWhilePaused() {
        val c = running()
        c.pause(t0 + 60_000)
        assertEquals(60_000, c.displayElapsedMillis(t0 + 60_000))
        assertEquals(60_000, c.displayElapsedMillis(t0 + 500_000))
    }

    @Test fun resumeReturnsPauseLengthAndAccumulates() {
        val c = running()
        c.pause(t0 + 60_000)
        assertEquals(40_000, c.resume(t0 + 100_000))
        assertFalse(c.isPaused)
        assertEquals(40_000, c.totalPauseMillis)
        // 100s later still: 160s total, 40s of it paused
        val now = t0 + 160_000
        assertEquals(120, c.activeSeconds(now))
        assertEquals(40, c.pauseSeconds(now))
        assertEquals(160, c.elapsedSeconds(now))
    }

    @Test fun multiplePausesAddUp() {
        val c = running()
        c.pause(t0 + 10_000); c.resume(t0 + 20_000)   // 10s pause
        c.pause(t0 + 30_000); c.resume(t0 + 35_000)   // 5s pause
        val now = t0 + 60_000
        assertEquals(15, c.pauseSeconds(now))
        assertEquals(45, c.activeSeconds(now))
        assertEquals(60, c.elapsedSeconds(now))
    }

    @Test fun endingWhilePausedCountsTheOpenPause() {
        val c = running()
        c.pause(t0 + 30_000)
        val now = t0 + 50_000
        assertEquals(30, c.activeSeconds(now))
        assertEquals(20, c.pauseSeconds(now))
    }

    @Test fun restoreContinuesARunningSession() {
        val c = SessionClock()
        c.restore(startMillis = t0, totalPauseMillis = 10_000, pauseStartMillis = null, isPaused = false)
        val now = t0 + 70_000
        assertEquals(60, c.activeSeconds(now))
        assertEquals(10, c.pauseSeconds(now))
    }

    @Test fun restoreContinuesAPausedSession() {
        val c = SessionClock()
        c.restore(startMillis = t0, totalPauseMillis = 0, pauseStartMillis = t0 + 30_000, isPaused = true)
        assertTrue(c.isPaused)
        assertEquals(30_000, c.displayElapsedMillis(t0 + 90_000))
        assertEquals(60, c.currentPauseMillis(t0 + 90_000) / 1000)
        assertEquals(30, c.activeSeconds(t0 + 90_000))
    }

    @Test fun startResetsPreviousSession() {
        val c = running()
        c.pause(t0 + 5_000); c.resume(t0 + 9_000)
        c.start(t0 + 100_000)
        assertEquals(0, c.totalPauseMillis)
        assertFalse(c.isPaused)
        assertEquals(10, c.activeSeconds(t0 + 110_000))
    }
}
