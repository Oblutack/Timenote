package com.oblutack.timenote.feature_timer.domain

enum class EventType { START, PAUSE, RESUME, END, NOTE }

data class TimelineEvent(
    val id: String,
    val title: String,
    val timestamp: String,
    val type: EventType,
    val isLastItem: Boolean = false
)

val mockTimelineEvents = listOf(
    TimelineEvent(id = "1", title = "Break started", timestamp = "14:45", type = EventType.PAUSE),
    TimelineEvent(id = "2", title = "Note: Email sent", timestamp = "14:25", type = EventType.NOTE, isLastItem = true)
)