package com.oblutack.timenote.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.ConstructedBy
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// 1. BUMP VERSION TO 2
@Database(entities = [TimenoteEntity::class, TagEntity::class, FolderEntity::class], version = 4)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timenoteDao(): TimenoteDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : androidx.room.RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

// 2. NEW MIGRATION OBJECT
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) { // <--- CHANGE 'invoke' to 'migrate'
        connection.execSQL("ALTER TABLE project_folders ADD COLUMN description TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE tags ADD COLUMN description TEXT DEFAULT NULL")
    }
}

// 2. NEW MIGRATION FOR AUDIO
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE timenotes ADD COLUMN audioPath TEXT DEFAULT NULL")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE timenotes ADD COLUMN voiceNotesJson TEXT NOT NULL DEFAULT '[]'")
    }
}