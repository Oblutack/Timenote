package com.oblutack.timenote.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TimenoteEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timenoteDao(): TimenoteDao
}