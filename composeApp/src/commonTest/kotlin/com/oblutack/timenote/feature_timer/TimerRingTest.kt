package com.oblutack.timenote.feature_timer

import com.oblutack.timenote.feature_timer.presentation.RING_LAP_SECONDS
import com.oblutack.timenote.feature_timer.presentation.ringFraction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimerRingTest {
    @Test fun oneLapIsOneHour() = assertEquals(3600L, RING_LAP_SECONDS)

    @Test fun theRingFillsInsideTheHour() {
        assertEquals(0.25f, ringFraction(900))
        assertEquals(0.5f, ringFraction(1800))
        assertEquals(0.75f, ringFraction(2700))
    }

    @Test fun afterAnHourItStartsOver() {
        assertEquals(0.25f, ringFraction(3600 + 900))
        assertEquals(0.5f, ringFraction(2 * 3600 + 1800))
    }

    @Test fun aRunningRingIsNeverEmpty() {
        // the very start and the exact end of a lap show a small dot, so a running timer cannot look idle
        assertTrue(ringFraction(0) > 0f)
        assertTrue(ringFraction(3600) > 0f)
        assertTrue(ringFraction(7200) < 0.01f)
    }

    @Test fun neverMoreThanAFullRing() {
        listOf(0L, 1L, 3599L, 3600L, 99_999L).forEach { assertTrue(ringFraction(it) in 0f..1f) }
    }
}
