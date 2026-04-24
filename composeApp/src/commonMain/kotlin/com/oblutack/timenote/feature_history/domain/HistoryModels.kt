package com.oblutack.timenote.feature_history.domain

import androidx.compose.ui.graphics.Color

// Represents a category/folder
data class TimenoteFolder(
    val id: String,
    val name: String,
    val sessionCount: Int,
    val color: Color
)

// Represents a completed timer session
data class PastSession(
    val id: String,
    val title: String,
    val description: String,
    val duration: String,
    val tags: List<TimenoteFolder>
)

// Mock Data based exactly on your Figma design
val mockFolders = listOf(
    TimenoteFolder("1", "Work", 12, Color(0xFF4FA8F9)),   // Blue
    TimenoteFolder("2", "Study", 8, Color(0xFF4CAF50)),   // Green
    TimenoteFolder("3", "Fitness", 5, Color(0xFFFF9800)), // Orange
    TimenoteFolder("4", "Personal", 3, Color(0xFF9C27B0)) // Purple
)

val mockSessions = listOf(
    PastSession(
        id = "1", title = "Deep Work Session", description = "Product roadmap planning", duration = "2h 45m",
        tags = listOf(mockFolders[0]) // Work tag
    ),
    PastSession(
        id = "2", title = "React Advanced Patterns", description = "Compound components study", duration = "3h 15m",
        tags = listOf(mockFolders[1]) // Study tag
    ),
    PastSession(
        id = "3", title = "Morning Workout", description = "Full body strength training", duration = "45m",
        tags = listOf(mockFolders[2]) // Fitness tag
    )
)