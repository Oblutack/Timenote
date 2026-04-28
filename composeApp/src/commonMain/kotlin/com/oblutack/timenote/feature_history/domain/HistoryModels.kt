package com.oblutack.timenote.feature_history.domain

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.core.ColorSerializer
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.serialization.Serializable

@Serializable
data class TimenoteFolder(
    val id: String,
    val name: String,
    val sessionCount: Int,
    @Serializable(with = ColorSerializer::class) val color: Color
)

@Serializable
data class Timenote(
    val id: String,
    val title: String,
    val description: String,
    val duration: String,
    val activeSeconds: Int,  // <--- NEW
    val pauseSeconds: Int,   // <--- NEW
    val createdAt: Long,     // <--- NEW
    val tags: List<TimenoteFolder>,
    val timelineEvents: List<TimelineEvent>
)

val mockFolders = listOf(
    TimenoteFolder("1", "Work", 12, Color(0xFF4FA8F9)),
    TimenoteFolder("2", "Study", 8, Color(0xFF4CAF50)),
    TimenoteFolder("3", "Fitness", 5, Color(0xFFFF9800)),
    TimenoteFolder("4", "Personal", 3, Color(0xFF9C27B0))
)

// We update the mock data to use the new Timenote class and empty events
val mockSessions = listOf(
    Timenote("1", "Deep Work Session", "Product roadmap planning", "2h 45m", 9900, 0, 1713870000000L, listOf(mockFolders[0]), emptyList()),
    Timenote("2", "React Advanced Patterns", "Compound components study", "3h 15m", 11700, 1800, 1713780000000L, listOf(mockFolders[1]), emptyList()),
    Timenote("3", "Morning Workout", "Full body strength training", "45m", 2700, 300, 1713690000000L, listOf(mockFolders[2]), emptyList())
)