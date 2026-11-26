package com.oblutack.timenote.core

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The two lines above a session's details: when it was, and from what time to what time. */
data class SessionHeader(val dateText: String, val timeRangeText: String) {
    /** "Wed 25 Nov · 6:40 – 7:28 PM" */
    val oneLine: String get() = "$dateText · $timeRangeText"
}

/**
 * A session is saved when it ends, so [createdAtMillis] is its end and the start is that minus focus and breaks. The time
 * is in the 12-hour style the app uses everywhere; the year is added only when it is not [currentYear].
 */
fun sessionHeader(
    createdAtMillis: Long,
    activeSeconds: Int,
    pauseSeconds: Int,
    zone: TimeZone,
    currentYear: Int
): SessionHeader {
    val endMillis = createdAtMillis
    val startMillis = endMillis - (activeSeconds + pauseSeconds).coerceAtLeast(0) * 1000L
    val start = Instant.fromEpochMilliseconds(startMillis).toLocalDateTime(zone)
    val end = Instant.fromEpochMilliseconds(endMillis).toLocalDateTime(zone)

    val weekday = start.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = start.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val year = if (start.year != currentYear) " ${start.year}" else ""
    val date = "$weekday ${start.dayOfMonth} $month$year"

    fun clock(hour: Int, minute: Int): String = "${if (hour % 12 == 0) 12 else hour % 12}:${minute.toString().padStart(2, '0')}"
    fun meridiem(hour: Int): String = if (hour >= 12) "PM" else "AM"

    val startClock = clock(start.hour, start.minute)
    val endClock = clock(end.hour, end.minute)
    val sameDay = start.date == end.date
    val range = when {
        // 6:40 – 7:28 PM
        sameDay && meridiem(start.hour) == meridiem(end.hour) -> "$startClock – $endClock ${meridiem(end.hour)}"
        // 11:20 AM – 12:10 PM
        sameDay -> "$startClock ${meridiem(start.hour)} – $endClock ${meridiem(end.hour)}"
        // past midnight
        else -> "$startClock ${meridiem(start.hour)} – $endClock ${meridiem(end.hour)} (next day)"
    }
    return SessionHeader(date, range)
}
