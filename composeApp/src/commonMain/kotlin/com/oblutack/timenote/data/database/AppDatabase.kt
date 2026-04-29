package com.oblutack.timenote.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

// NEW: Added TagEntity, bumped version to 2
@Database(entities = [TimenoteEntity::class, TagEntity::class, FolderEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timenoteDao(): TimenoteDao
}