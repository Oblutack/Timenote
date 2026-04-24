package com.oblutack.timenote.feature_timer.domain

import androidx.compose.ui.graphics.Color

enum class EventType { START, PAUSE, RESUME, END, NOTE }

data class TimelineEvent(
    val id: String,
    val title: String,
    val timestamp: String,
    val type: EventType,
    val isLastItem: Boolean = false,
    val color: Color? = null
)
