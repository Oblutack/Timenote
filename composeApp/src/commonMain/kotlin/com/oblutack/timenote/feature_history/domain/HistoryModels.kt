package com.oblutack.timenote.feature_history.domain

import com.oblutack.timenote.ui.theme.Palette
import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.core.ColorSerializer
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate

@Serializable
data class ProjectFolder(
    val id: String,
    val name: String,
    val description: String? = null,
    @Serializable(with = ColorSerializer::class) val color: Color,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

@Serializable
data class TimenoteFolder(
    val id: String,
    val name: String,
    val description: String? = null,
    val sessionCount: Int,
    @Serializable(with = ColorSerializer::class) val color: Color,
)

/** The colour that stands for a session: the colour of its first tag, or [untagged] when it has none. */
fun List<TimenoteFolder>.identityColor(untagged: Color): Color = firstOrNull()?.color ?: untagged

/** A text of a note that lost a merge between devices and can be viewed, restored or dismissed. */
data class TextConflict(
    val noteId: String,
    val textHash: String,
    val text: String,
    /** When (epoch millis) and on which device that text was written. */
    val writtenAt: Long,
    val deviceId: String,
    val detectedAt: Long
)

@Serializable
data class Timenote(
    val id: String,
    val folderId: String? = null,
    val title: String,
    val description: String,
    val audioPath: String? = null,
    val voiceNotes: List<String> = emptyList(),
    val duration: String,
    val activeSeconds: Int,  // <--- NEW
    val pauseSeconds: Int,   // <--- NEW
    val createdAt: Long,     // <--- NEW
    val tags: List<TimenoteFolder>,
    val timelineEvents: List<TimelineEvent>,
    val parentTimenoteId: String? = null,
    val parentWaypointId: String? = null,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

val mockFolders = listOf(
    TimenoteFolder("1", "Work", null, 12, Palette.Blue),    // <-- Added 'null,'
    TimenoteFolder("2", "Study", null, 8, Palette.Green),    // <-- Added 'null,'
    TimenoteFolder("3", "Fitness", null, 5, Palette.Orange),  // <-- Added 'null,'
    TimenoteFolder("4", "Personal", null, 3, Palette.Purple)  // <-- Added 'null,'
)

data class DailySummary(
    val date: LocalDate,
    val totalSeconds: Int,
    val sessionCount: Int,
    val topTag: TimenoteFolder?
)


