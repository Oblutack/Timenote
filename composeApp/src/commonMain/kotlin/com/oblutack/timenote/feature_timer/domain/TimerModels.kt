package com.oblutack.timenote.feature_timer.domain

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.core.ColorSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class EventType { START, PAUSE, RESUME, END, NOTE }

@Serializable
data class TimelineEvent(
    val id: String,
    val title: String,
    val timestamp: String,
    val type: EventType,
    val isLastItem: Boolean = false,

    // We tell it to use our custom translator for the Color!
    @Serializable(with = ColorSerializer::class) val color: Color? = null,
    val audioPath: String? = null
)

/** The note that says where a branch came from: part of the structure, not something to edit. */
private const val BRANCH_NOTE_PREFIX = "Branched from:"

/** A typed note can be edited while the session runs. Voice memos, the branch note and the clock's entries cannot. */
fun TimelineEvent.canEditWhileRunning(): Boolean =
    type == EventType.NOTE && audioPath == null && !title.startsWith(BRANCH_NOTE_PREFIX)

/** Notes and voice memos can be deleted while the session runs; start, pause, resume and end come from the clock. */
fun TimelineEvent.canDeleteWhileRunning(): Boolean =
    type == EventType.NOTE && !title.startsWith(BRANCH_NOTE_PREFIX)

/** A waypoint that was deleted, with where it sat (how many entries were older than it, itself not counted), so Undo puts it back in place. */
data class DeletedWaypoint(val event: TimelineEvent, val olderCount: Int)

@Serializable
data class ActiveSessionBackup(
    val sessionTitle: String,
    val startTimeMillis: Long,
    val totalPauseMillis: Long,
    val lastPauseStartTimeMillis: Long?, // If not null, it means the app was swiped away WHILE paused!
    val isPaused: Boolean,
    val timelineEvents: List<TimelineEvent>,
    val selectedFolderId: String?,
    val selectedCategoryIds: List<String>,
    val parentTimenoteId: String? = null,
    val parentWaypointId: String? = null
)

