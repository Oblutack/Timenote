package com.oblutack.timenote.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val isDeleted: Boolean,
    val deletedAt: Long? = null,
    /** Last time any field of this note changed (epoch millis). Used by sync to find what needs uploading. */
    val updatedAt: Long = 0L
)