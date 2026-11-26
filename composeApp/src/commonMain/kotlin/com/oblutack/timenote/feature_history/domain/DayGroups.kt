package com.oblutack.timenote.feature_history.domain

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.core.humanDuration
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One part of the colour bar of a day: how much of the day's focus time had this colour. */
data class BarSegment(val color: Color, val seconds: Int)

/**
 * The sessions of one day (or the pinned ones) under one header. [date] is null for the pinned group, which has a title but no
 * total: pinned sessions come from any day.
 */
data class DayGroup(
    val date: LocalDate?,
    val title: String,
    val focusSeconds: Int,
    val sessions: List<Timenote>,
    val colorBar: List<BarSegment>
) {
    /** "Today · 3h 22m" (the pinned group is just "Pinned"). */
    val headerText: String get() = if (date == null) title else "$title · ${humanDuration(focusSeconds)}"
}

private fun dateOf(millis: Long, zone: TimeZone) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date

private fun dayTitle(date: LocalDate, today: LocalDate): String {
    if (date == today) return "Today"
    if (date.toEpochDays() == today.toEpochDays() - 1) return "Yesterday"
    val weekday = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val year = if (date.year != today.year) " ${date.year}" else ""
    return "$weekday ${date.dayOfMonth} $month$year"
}

/**
 * Splits an already sorted list into groups, keeping the order: pinned sessions first under "Pinned", then one group per day in
 * the order the days first appear. The total of a day is its focus time; the colour bar shows which tag the focus time went to
 * (grey for sessions without a tag).
 */
fun groupSessionsByDay(
    sessions: List<Timenote>,
    today: LocalDate,
    zone: TimeZone,
    fallbackMillis: Long,
    untagged: Color
): List<DayGroup> {
    fun bar(list: List<Timenote>): List<BarSegment> {
        val seconds = LinkedHashMap<Color, Int>()
        list.forEach { seconds[it.tags.identityColor(untagged)] = (seconds[it.tags.identityColor(untagged)] ?: 0) + it.activeSeconds }
        return seconds.map { BarSegment(it.key, it.value) }.sortedByDescending { it.seconds }
    }

    val groups = mutableListOf<DayGroup>()
    val (pinned, rest) = sessions.partition { it.isPinned }
    if (pinned.isNotEmpty()) groups += DayGroup(null, "Pinned", pinned.sumOf { it.activeSeconds }, pinned, bar(pinned))

    val byDay = LinkedHashMap<LocalDate, MutableList<Timenote>>()
    rest.forEach { session ->
        val date = dateOf(if (session.createdAt > 0L) session.createdAt else fallbackMillis, zone)
        byDay.getOrPut(date) { mutableListOf() } += session
    }
    byDay.forEach { (date, list) ->
        groups += DayGroup(date, dayTitle(date, today), list.sumOf { it.activeSeconds }, list, bar(list))
    }
    return groups
}

/** How many of the check boxes (`- [ ]` and `- [x]`) of a description are ticked, or null when it has none. */
fun checklistProgress(description: String): Pair<Int, Int>? {
    val box = Regex("^\\s*[-*] \\[([ xX])\\]")
    var done = 0
    var total = 0
    description.lines().forEach { line ->
        val match = box.find(line) ?: return@forEach
        total++
        if (match.groupValues[1] != " ") done++
    }
    return if (total == 0) null else done to total
}
