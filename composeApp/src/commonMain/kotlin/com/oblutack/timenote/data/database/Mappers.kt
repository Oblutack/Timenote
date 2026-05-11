package com.oblutack.timenote.data.database

import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 1. Translates a Timenote into a Database Entity (Turns lists into JSON Strings)
fun Timenote.toEntity(): TimenoteEntity {
    return TimenoteEntity(
        id = this.id,
        folderId = this.folderId,
        title = this.title,
        description = this.description,
        audioPath = this.audioPath,
        duration = this.duration,
        activeSeconds = this.activeSeconds, // <--- NEW
        pauseSeconds = this.pauseSeconds,   // <--- NEW
        createdAt = this.createdAt,         // <--- NEW
        tagsJson = Json.encodeToString(this.tags),
        timelineEventsJson = Json.encodeToString(this.timelineEvents),
        isDeleted = this.isDeleted
    )
}

fun TimenoteEntity.toDomain(): Timenote {
    return Timenote(
        id = this.id,
        folderId = this.folderId,
        title = this.title,
        description = this.description,
        audioPath = this.audioPath,
        duration = this.duration,
        activeSeconds = this.activeSeconds, // <--- NEW
        pauseSeconds = this.pauseSeconds,   // <--- NEW
        createdAt = this.createdAt,         // <--- NEW
        tags = Json.decodeFromString<List<TimenoteFolder>>(this.tagsJson),
        timelineEvents = Json.decodeFromString<List<TimelineEvent>>(this.timelineEventsJson)
    )
}

// --- NEW: Folder Mappers ---
fun com.oblutack.timenote.feature_history.domain.ProjectFolder.toEntity(): FolderEntity {
    return FolderEntity(
        id = this.id,
        name = this.name,
        colorLong = this.color.value.toLong(),
        createdAt = this.createdAt,
        isDeleted = this.isDeleted,
        description = this.description,
    )
}

fun FolderEntity.toDomain(): com.oblutack.timenote.feature_history.domain.ProjectFolder {
    return com.oblutack.timenote.feature_history.domain.ProjectFolder(
        id = this.id,
        name = this.name,
        description = this.description,
        color = androidx.compose.ui.graphics.Color(this.colorLong.toULong()),
        createdAt = this.createdAt
    )
}