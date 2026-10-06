package com.oblutack.timenote.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.ConstructedBy
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.RoomDatabaseConstructor

// Bump this together with adding a MIGRATION_n_m below and listing it in ALL_MIGRATIONS.
// MigrationsTest fails if the chain from version 2 to DATABASE_VERSION has a gap.
const val DATABASE_VERSION = 7

@Database(entities = [TimenoteEntity::class, TagEntity::class, FolderEntity::class], version = DATABASE_VERSION)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timenoteDao(): TimenoteDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

// Version 1 has no migration: see fallbackToDestructiveMigrationFrom in DatabaseBuilder.kt.

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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE timenotes ADD COLUMN parentTimenoteId TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE timenotes ADD COLUMN parentWaypointId TEXT DEFAULT NULL")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE project_folders ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE timenotes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE project_folders ADD COLUMN deletedAt INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE timenotes ADD COLUMN deletedAt INTEGER DEFAULT NULL")
    }
}

/** Every migration, registered in one place so a new one can't be forgotten in MainActivity. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
)
