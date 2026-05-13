package com.oblutack.timenote.feature_history.domain

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.core.ColorSerializer
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.serialization.Serializable

@Serializable
data class ProjectFolder(
    val id: String,
    val name: String,
    val description: String? = null,
    @Serializable(with = ColorSerializer::class) val color: Color,
    val createdAt: Long,
    val isDeleted: Boolean = false
)

@Serializable
data class TimenoteFolder(
    val id: String,
    val name: String,
    val description: String? = null,
    val sessionCount: Int,
    @Serializable(with = ColorSerializer::class) val color: Color,
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
    val isDeleted: Boolean = false
)

val mockFolders = listOf(
    TimenoteFolder("1", "Work", null, 12, Color(0xFF4FA8F9)),    // <-- Added 'null,'
    TimenoteFolder("2", "Study", null, 8, Color(0xFF4CAF50)),    // <-- Added 'null,'
    TimenoteFolder("3", "Fitness", null, 5, Color(0xFFFF9800)),  // <-- Added 'null,'
    TimenoteFolder("4", "Personal", null, 3, Color(0xFF9C27B0))  // <-- Added 'null,'
)

