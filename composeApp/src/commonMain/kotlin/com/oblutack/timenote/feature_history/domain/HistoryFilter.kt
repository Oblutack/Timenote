package com.oblutack.timenote.feature_history.domain

import com.oblutack.timenote.feature_timer.domain.EventType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Which days the list shows. */
sealed interface DateFilter {
    data object Any : DateFilter
    data object Today : DateFilter
    data object Last7Days : DateFilter
    data object Last30Days : DateFilter
    data object ThisMonth : DateFilter
    /** One day picked on the heatmap. */
    data class Day(val date: LocalDate) : DateFilter
}

/** Everything the filter bar can narrow the list by (the search words are kept apart). */
data class HistoryFilter(
    val date: DateFilter = DateFilter.Any,
    val folderId: String? = null,
    val tagIds: Set<String> = emptySet(),
    val hasNotes: Boolean = false,
    val hasVoice: Boolean = false,
    val hasBranches: Boolean = false,
    val pinnedOnly: Boolean = false
) {
    /** True when any filter is on (the search words do not count here). */
    val isActive: Boolean
        get() = date != DateFilter.Any || folderId != null || tagIds.isNotEmpty() || hasNotes || hasVoice || hasBranches || pinnedOnly
}

/** The notes a person wrote: not the clock's entries and not the line that says where a branch came from. */
fun noteCountOf(session: Timenote): Int =
    session.timelineEvents.count { it.type == EventType.NOTE && !it.title.startsWith("Branched from:") }

/** A session has a voice memo when one is attached to it or recorded during it. */
fun hasVoice(session: Timenote): Boolean = session.voiceNotes.isNotEmpty() || session.timelineEvents.any { it.audioPath != null }

/**
 * What the person wrote in the session's notes: the text without the "Note: " prefix, and not the clock's entries, the line
 * that says where a branch came from, or voice memos. (Searching "note" must not find every note.)
 */
fun writtenNoteTexts(session: Timenote): List<String> =
    session.timelineEvents
        .filter { it.type == EventType.NOTE && it.audioPath == null && !it.title.startsWith("Branched from:") }
        .map { it.title.removePrefix("Note: ") }

private fun matchesQuery(session: Timenote, query: String): Boolean =
    session.title.contains(query, ignoreCase = true) ||
        session.description.contains(query, ignoreCase = true) ||
        session.tags.any { it.name.contains(query, ignoreCase = true) } ||
        writtenNoteTexts(session).any { it.contains(query, ignoreCase = true) }

private fun matchesDate(filter: DateFilter, date: LocalDate, today: LocalDate): Boolean = when (filter) {
    DateFilter.Any -> true
    DateFilter.Today -> date == today
    DateFilter.Last7Days -> date.toEpochDays() in (today.toEpochDays() - 6)..today.toEpochDays()
    DateFilter.Last30Days -> date.toEpochDays() in (today.toEpochDays() - 29)..today.toEpochDays()
    DateFilter.ThisMonth -> date.year == today.year && date.month == today.month
    is DateFilter.Day -> date == filter.date
}

/**
 * The sessions that pass every filter and contain the [query] words (in the title, the description, a tag name or a note). A
 * session passes the tag filter when it has any of the chosen tags. The order of [all] is kept.
 */
fun filterSessions(
    all: List<Timenote>,
    filter: HistoryFilter,
    query: String,
    today: LocalDate,
    zone: TimeZone,
    fallbackMillis: Long
): List<Timenote> {
    val parentIds = if (filter.hasBranches) all.mapNotNull { it.parentTimenoteId }.toSet() else emptySet()
    return all.filter { session ->
        val date = Instant.fromEpochMilliseconds(if (session.createdAt > 0L) session.createdAt else fallbackMillis).toLocalDateTime(zone).date
        matchesDate(filter.date, date, today) &&
            (filter.folderId == null || session.folderId == filter.folderId) &&
            (filter.tagIds.isEmpty() || session.tags.any { it.id in filter.tagIds }) &&
            (!filter.hasNotes || noteCountOf(session) > 0) &&
            (!filter.hasVoice || hasVoice(session)) &&
            (!filter.hasBranches || session.id in parentIds) &&
            (!filter.pinnedOnly || session.isPinned) &&
            (query.isBlank() || matchesQuery(session, query))
    }
}
