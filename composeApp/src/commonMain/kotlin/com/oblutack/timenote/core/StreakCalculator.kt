package com.oblutack.timenote.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Returns (currentStreak, bestStreak) in days for the given set of active dates.
 * The current streak stays alive if the last active day is today or yesterday.
 */
fun calculateStreaks(activeDates: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
    if (activeDates.isEmpty()) return Pair(0, 0)

    val oneDay = DatePeriod(days = 1)

    var best = 0
    var run = 0
    var previous: LocalDate? = null
    activeDates.sorted().forEach { date ->
        run = if (previous != null && previous!!.plus(oneDay) == date) run + 1 else 1
        if (run > best) best = run
        previous = date
    }

    val yesterday = today.minus(oneDay)
    var cursor = when {
        today in activeDates -> today
        yesterday in activeDates -> yesterday
        else -> return Pair(0, best)
    }
    var current = 0
    while (cursor in activeDates) {
        current++
        cursor = cursor.minus(oneDay)
    }
    return Pair(current, best)
}
