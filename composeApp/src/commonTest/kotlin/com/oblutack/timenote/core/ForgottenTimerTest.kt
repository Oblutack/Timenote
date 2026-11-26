package com.oblutack.timenote.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForgottenTimerTest {
    private val threeHours = 3 * 60 * 60 * 1000L

    @Test fun withoutHistoryItIsThreeHours() = assertEquals(threeHours, forgottenTimerThresholdMillis(emptyList()))

    @Test fun shortSessionsStillGetThreeHours() =
        assertEquals(threeHours, forgottenTimerThresholdMillis(listOf(1_800, 2_700, 3_600, 1_200)))

    @Test fun peopleWhoWorkLongBlocksAreAskedLater() {
        // typical 5 h -> asked after 10 h
        val fiveHours = 5 * 3600
        assertEquals(10 * 60 * 60 * 1000L, forgottenTimerThresholdMillis(List(7) { fiveHours }))
    }

    @Test fun theTypicalLengthIsTheMedianSoOneLongDayDoesNotMoveIt() {
        val sessions = List(9) { 3_600 } + listOf(20 * 3600)
        assertEquals(threeHours, forgottenTimerThresholdMillis(sessions))
    }

    @Test fun aMedianBetweenTwoValuesIsTheirMean() {
        // 2 h and 4 h -> median 3 h -> twice is 6 h
        assertEquals(6 * 60 * 60 * 1000L, forgottenTimerThresholdMillis(listOf(2 * 3600, 4 * 3600)))
    }

    @Test fun emptySessionsDoNotCount() {
        assertEquals(threeHours, forgottenTimerThresholdMillis(listOf(0, 0, 0)))
    }

    @Test fun timelineTimesAreReadBackAsSeconds() {
        assertEquals(0, parseDurationSeconds("00:00:00"))
        assertEquals(3_725, parseDurationSeconds("01:02:05"))
        assertEquals(360_000, parseDurationSeconds("100:00:00"))
    }

    @Test fun textThatIsNotAClockTimeIsRejected() {
        assertNull(parseDurationSeconds(""))
        assertNull(parseDurationSeconds("12:30"))
        assertNull(parseDurationSeconds("aa:bb:cc"))
        assertNull(parseDurationSeconds("00:-1:00"))
    }

    @Test fun durationsInWords() {
        assertEquals("0 min", humanDuration(20))
        assertEquals("45 min", humanDuration(45 * 60))
        assertEquals("3 h", humanDuration(3 * 3600))
        assertEquals("5 h 12 min", humanDuration(5 * 3600 + 12 * 60 + 30))
        assertEquals("0 min", humanDuration(-5))
    }
}
