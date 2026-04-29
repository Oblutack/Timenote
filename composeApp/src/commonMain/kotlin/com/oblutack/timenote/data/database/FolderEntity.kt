package com.oblutack.timenote.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorLong: Long,
    val createdAt: Long
)