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

@Serializable
data class ActiveSessionBackup(
    val sessionTitle: String,
    val startTimeMillis: Long,
    val totalPauseMillis: Long,
    val lastPauseStartTimeMillis: Long?, // If not null, it means the app was swiped away WHILE paused!
    val isPaused: Boolean,
    val timelineEvents: List<TimelineEvent>,
    val selectedFolderId: String?,
    val selectedCategoryIds: List<String>
)

