package com.oblutack.timenote.feature_history.domain

import com.oblutack.timenote.core.SessionHeader
import com.oblutack.timenote.core.formatDuration
import com.oblutack.timenote.core.parseDurationSeconds
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent

/** One line of the notes in an exported session: when, and what. */
private data class ExportedNote(val time: String, val text: String)

private fun exportedNotes(events: List<TimelineEvent>): List<ExportedNote> =
    events
        .filter { it.type == EventType.NOTE && !it.title.startsWith("Branched from:") }
        // oldest first, whatever order the session stores them in
        .sortedBy { parseDurationSeconds(it.timestamp) ?: Int.MAX_VALUE }
        .map {
            val text = if (it.audioPath != null) "Voice memo" else it.title.removePrefix("Note: ")
            ExportedNote(it.timestamp, text)
        }

private fun Timenote.totals(): String =
    "Focus ${formatDuration(activeSeconds)} · Breaks ${formatDuration(pauseSeconds)} · Total ${formatDuration(activeSeconds + pauseSeconds)}"

/** A session as plain text, for the share sheet: readable in a message or an email. */
fun sessionAsText(note: Timenote, folderName: String?, header: SessionHeader): String = buildString {
    appendLine(note.title.ifBlank { "Untitled Session" })
    appendLine(header.oneLine)
    if (folderName != null) appendLine("Folder: $folderName")
    if (note.tags.isNotEmpty()) appendLine("Tags: ${note.tags.joinToString(", ") { it.name }}")
    appendLine(note.totals())
    if (note.description.isNotBlank()) {
        appendLine()
        appendLine(note.description.trim())
    }
    val notes = exportedNotes(note.timelineEvents)
    if (notes.isNotEmpty()) {
        appendLine()
        appendLine("Notes")
        notes.forEach { appendLine("${it.time}  ${it.text}") }
    }
}.trimEnd()

/** A session as Markdown, for pasting into a notes app. The description is already Markdown and is kept as it is. */
fun sessionAsMarkdown(note: Timenote, folderName: String?, header: SessionHeader): String = buildString {
    appendLine("# ${note.title.ifBlank { "Untitled Session" }}")
    appendLine()
    appendLine("*${header.oneLine}*")
    appendLine()
    if (folderName != null) appendLine("- **Folder:** $folderName")
    if (note.tags.isNotEmpty()) appendLine("- **Tags:** ${note.tags.joinToString(", ") { it.name }}")
    appendLine("- **Focus:** ${formatDuration(note.activeSeconds)}")
    appendLine("- **Breaks:** ${formatDuration(note.pauseSeconds)}")
    appendLine("- **Total:** ${formatDuration(note.activeSeconds + note.pauseSeconds)}")
    if (note.description.isNotBlank()) {
        appendLine()
        appendLine(note.description.trim())
    }
    val notes = exportedNotes(note.timelineEvents)
    if (notes.isNotEmpty()) {
        appendLine()
        appendLine("## Notes")
        appendLine()
        notes.forEach { appendLine("- `${it.time}` ${it.text}") }
    }
}.trimEnd()
