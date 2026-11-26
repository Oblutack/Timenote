package com.oblutack.timenote.feature_history.domain

import com.oblutack.timenote.core.parseDurationSeconds
import com.oblutack.timenote.core.sessionHeader
import com.oblutack.timenote.feature_timer.domain.EventType
import kotlinx.datetime.TimeZone

/** A session that mentions another one: who, when, and the words around the mention. */
data class Backlink(
    val sessionId: String,
    val title: String,
    val dateText: String,
    val snippet: String
)

private val MENTION = Regex("@\\[(.*?)\\]\\((.*?)\\)")

/** How much of the line is kept on each side of the mention. */
private const val SNIPPET_BEFORE = 30
private const val SNIPPET_AFTER = 40

/**
 * The sessions whose description or notes mention [targetId] (`@[Title](id)`), newest first, one row per session. The session
 * itself and sessions in the trash do not count. The snippet is the line around the mention, in plain words.
 */
fun findBacklinks(targetId: String, sessions: List<Timenote>, zone: TimeZone, currentYear: Int): List<Backlink> =
    sessions
        .filter { it.id != targetId && !it.isDeleted }
        .mapNotNull { session ->
            val snippet = firstMentionSnippet(targetId, session) ?: return@mapNotNull null
            session to snippet
        }
        .sortedByDescending { (session, _) -> session.createdAt }
        .map { (session, snippet) ->
            Backlink(
                sessionId = session.id,
                title = session.title.ifBlank { "Untitled Session" },
                dateText = sessionHeader(session.createdAt, session.activeSeconds, session.pauseSeconds, zone, currentYear).dateText,
                snippet = snippet
            )
        }

/** The description is searched first, then the notes from the oldest to the newest. */
private fun firstMentionSnippet(targetId: String, session: Timenote): String? {
    val texts = buildList {
        add(session.description)
        session.timelineEvents
            .filter { it.type == EventType.NOTE }
            .sortedBy { parseDurationSeconds(it.timestamp) ?: Int.MAX_VALUE }
            .forEach { add(it.title.removePrefix("Note: ")) }
    }
    for (text in texts) {
        for (line in text.lines()) {
            snippetFromLine(targetId, line)?.let { return it }
        }
    }
    return null
}

/** The line with every mention shown as its title, cut around the mention of [targetId]; null when it has none. */
private fun snippetFromLine(targetId: String, line: String): String? {
    if (MENTION.findAll(line).none { it.groupValues[2] == targetId }) return null
    val plain = StringBuilder()
    var targetStart = -1
    var targetEnd = -1
    var last = 0
    for (match in MENTION.findAll(line)) {
        plain.append(line, last, match.range.first)
        if (match.groupValues[2] == targetId && targetStart < 0) targetStart = plain.length
        plain.append(match.groupValues[1])
        if (match.groupValues[2] == targetId && targetEnd < 0) targetEnd = plain.length
        last = match.range.last + 1
    }
    plain.append(line, last, line.length)

    val text = plain.toString()
    val from = maxOf(0, targetStart - SNIPPET_BEFORE)
    val to = minOf(text.length, targetEnd + SNIPPET_AFTER)
    val cut = text.substring(from, to)
    val cleaned = cut
        .replace("**", "").replace("~~", "")
        .replace(Regex("^\\s*(#{1,3} |- \\[[ xX]\\] |[-*] )"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return (if (from > 0) "…" else "") + cleaned + (if (to < text.length) "…" else "")
}
