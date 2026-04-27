package com.oblutack.timenote.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import androidx.compose.ui.graphics.Color

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sessionCount: Int,
    val colorLong: Long // SQLite can't store Colors, so we store the raw Long value!
)

// Mappers to translate between DB and Domain
fun TimenoteFolder.toEntity(): TagEntity {
    return TagEntity(
        id = this.id,
        name = this.name,
        sessionCount = this.sessionCount,
        colorLong = this.color.value.toLong()
    )
}

fun TagEntity.toDomain(): TimenoteFolder {
    return TimenoteFolder(
        id = this.id,
        name = this.name,
        sessionCount = this.sessionCount,
        color = Color(this.colorLong.toULong())
    )
}