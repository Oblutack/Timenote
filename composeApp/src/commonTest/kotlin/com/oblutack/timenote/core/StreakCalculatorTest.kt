package com.oblutack.timenote.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakCalculatorTest {
    private val today = LocalDate(2026, 3, 10)
    private fun d(day: Int) = LocalDate(2026, 3, day)

    @Test fun noActivity() = assertEquals(Pair(0, 0), calculateStreaks(emptySet(), today))

    @Test fun onlyToday() = assertEquals(Pair(1, 1), calculateStreaks(setOf(d(10)), today))

    @Test fun streakIncludingToday() =
        assertEquals(Pair(3, 3), calculateStreaks(setOf(d(8), d(9), d(10)), today))

    @Test fun streakStaysAliveWhenLastActiveWasYesterday() =
        assertEquals(Pair(2, 2), calculateStreaks(setOf(d(8), d(9)), today))

    @Test fun streakBrokenAfterTwoIdleDaysKeepsBest() =
        assertEquals(Pair(0, 3), calculateStreaks(setOf(d(5), d(6), d(7)), today))

    @Test fun bestStreakCanBeOlderThanCurrent() =
        assertEquals(Pair(2, 4), calculateStreaks(setOf(d(1), d(2), d(3), d(4), d(9), d(10)), today))

    @Test fun streakCrossesMonthBoundary() =
        assertEquals(Pair(3, 3), calculateStreaks(
            setOf(LocalDate(2026, 2, 27), LocalDate(2026, 2, 28), LocalDate(2026, 3, 1)),
            LocalDate(2026, 3, 1)
        ))
}
