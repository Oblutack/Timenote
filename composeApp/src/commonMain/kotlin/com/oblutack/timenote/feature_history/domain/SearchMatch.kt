package com.oblutack.timenote.feature_history.domain

import com.oblutack.timenote.core.parseDurationSeconds
import com.oblutack.timenote.core.plainText
import com.oblutack.timenote.feature_timer.domain.EventType

/** A short piece of text with the parts that matched the search words, as positions inside [text]. */
data class MatchSnippet(val text: String, val ranges: List<IntRange>)

private const val BEFORE = 30
private const val AFTER = 50

/** Where [query] appears in [text], ignoring upper and lower case; the ranges do not overlap. */
fun findMatchRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val at = text.indexOf(needle, from, ignoreCase = true)
        if (at < 0) break
        ranges += at until at + needle.length
        from = at + needle.length
    }
    return ranges
}

/** The words around the first match in [text] (already plain), with the match positions moved into the cut. */
private fun snippetAround(text: String, query: String): MatchSnippet? {
    val first = findMatchRanges(text, query).firstOrNull() ?: return null
    val from = maxOf(0, first.first - BEFORE)
    val to = minOf(text.length, first.last + 1 + AFTER)
    val cut = text.substring(from, to)
    val prefix = if (from > 0) "…" else ""
    val suffix = if (to < text.length) "…" else ""
    val ranges = findMatchRanges(cut, query).map { (it.first + prefix.length)..(it.last + prefix.length) }
    return MatchSnippet(prefix + cut + suffix, ranges)
}

/**
 * Why a session is in the search results: the words around the first match in its description, then in its notes (oldest
 * first). Null when only the title (or a tag) matched, since the title is shown anyway. Markdown is shown as plain words.
 */
fun searchSnippet(session: Timenote, query: String): MatchSnippet? {
    if (query.isBlank()) return null
    snippetAround(plainText(session.description), query)?.let { return it }
    session.timelineEvents
        .filter { it.type == EventType.NOTE && it.audioPath == null && !it.title.startsWith("Branched from:") }
        .sortedBy { parseDurationSeconds(it.timestamp) ?: Int.MAX_VALUE }
        .forEach { event ->
            snippetAround(plainText(event.title.removePrefix("Note: ")), query)?.let { return it }
        }
    return null
}
