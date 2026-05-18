package com.oblutack.timenote.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.oblutack.timenote.feature_history.domain.Timenote
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// This represents the actual SQL Table
@Entity(tableName = "timenotes")
data class TimenoteEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val title: String,
    val description: String,
    val audioPath: String?,
    val voiceNotesJson: String,
    val duration: String,
    val activeSeconds: Int,  // <--- NEW
    val pauseSeconds: Int,   // <--- NEW
    val createdAt: Long,     // <--- NEW
    val tagsJson: String,
    val timelineEventsJson: String,
    val parentTimenoteId: String?,
    val parentWaypointId: String?,
    val isPinned: Boolean,
    val isDeleted: Boolean
)