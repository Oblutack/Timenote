package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oblutack.timenote.core.sessionHeader
import com.oblutack.timenote.feature_history.domain.DayGroup
import com.oblutack.timenote.feature_history.domain.HistoryDensity
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.checklistProgress
import com.oblutack.timenote.feature_history.domain.identityColor
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.getCurrentTimeMillis
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The line of facts under the title of a compact row: when, where, and what is in it. Pure so it can be tested.
 * Example: "6:40 – 7:28 PM · Thesis · 3 notes · ☑ 2/4 · Voice · ↳ Branch".
 */
fun sessionFactsLine(
    timeRange: String,
    folderName: String?,
    noteCount: Int,
    checklist: Pair<Int, Int>?,
    hasVoice: Boolean,
    isBranch: Boolean,
    branchCount: Int
): String = listOfNotNull(
    timeRange,
    folderName,
    when (noteCount) { 0 -> null; 1 -> "1 note"; else -> "$noteCount notes" },
    checklist?.let { "☑ ${it.first}/${it.second}" },
    if (hasVoice) "Voice" else null,
    if (isBranch) "↳ Branch" else null,
    when (branchCount) { 0 -> null; 1 -> "1 branch"; else -> "$branchCount branches" }
).joinToString(" · ")

/** The notes a person wrote: not the clock's entries and not the line that says where a branch came from. */
fun noteCountOf(session: Timenote): Int =
    session.timelineEvents.count { it.type == EventType.NOTE && !it.title.startsWith("Branched from:") }

/** Compact row: a dot in the session's colour, the title and facts, the duration. */
@Composable
fun SessionRow(
    session: Timenote,
    allSessions: List<Timenote>,
    folderName: String?,
    onClick: () -> Unit
) {
    val header = sessionHeader(
        createdAtMillis = if (session.createdAt > 0L) session.createdAt else getCurrentTimeMillis(),
        activeSeconds = session.activeSeconds,
        pauseSeconds = session.pauseSeconds,
        zone = TimeZone.currentSystemDefault(),
        currentYear = Instant.fromEpochMilliseconds(getCurrentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).year
    )
    val facts = sessionFactsLine(
        timeRange = header.timeRangeText,
        folderName = folderName,
        noteCount = noteCountOf(session),
        checklist = checklistProgress(session.description),
        hasVoice = session.voiceNotes.isNotEmpty() || session.timelineEvents.any { it.audioPath != null },
        isBranch = session.parentTimenoteId != null,
        branchCount = allSessions.count { it.parentTimenoteId == session.id }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TnShape.Medium)
            .background(SurfaceDark)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(session.tags.identityColor(TnTheme.colors.untagged)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = session.title.ifBlank { "Untitled Session" },
                color = TextPrimary,
                fontSize = TnType.BodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = facts, color = TextSecondary, fontSize = TnType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = session.duration, color = TextSecondary, fontFamily = TnTheme.mono, fontSize = TnType.Body)
    }
}

/** "Today · 3h 22m" over a thin bar showing which tags the day's focus time went to. */
@Composable
fun DayHeader(group: DayGroup) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)) {
        Text(group.headerText, color = TextPrimary, fontSize = TnType.Body, fontWeight = FontWeight.SemiBold)
        val total = group.colorBar.sumOf { it.seconds }
        if (group.date != null && total > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth().height(4.dp).clip(TnShape.Pill)) {
                group.colorBar.forEach { segment ->
                    Box(modifier = Modifier.weight(segment.seconds.toFloat()).fillMaxSize().background(segment.color))
                }
            }
        }
    }
}

/** One session of the list in either density, with swipe-left to delete (the delete itself asks about branches). */
@Composable
fun HistorySessionItem(
    session: Timenote,
    allSessions: List<Timenote>,
    folderName: String?,
    density: HistoryDensity,
    enableHaptics: Boolean,
    onDelete: (Timenote) -> Unit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete(session)
                false // Bounce back
            } else false
        }
    )
    val shape = if (density == HistoryDensity.Compact) TnShape.Medium else TnShape.Card
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().clip(shape).background(TnTheme.colors.danger).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = androidx.compose.ui.graphics.Color.White)
            }
        },
        content = {
            if (density == HistoryDensity.Compact) {
                SessionRow(session = session, allSessions = allSessions, folderName = folderName, onClick = onClick)
            } else {
                SessionCard(session = session, allSessions = allSessions, onClick = onClick)
            }
        }
    )
}
