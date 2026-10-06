package com.oblutack.timenote.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// This function takes the platform-specific database (Android/iOS)
// and attaches the universal SQLite driver to it!
fun instantiateDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        // Version 1 only ever existed in development builds, and its real schema does not match the
        // exported 1.json, so it cannot be migrated. Reset it instead of crashing at every launch
        // (which the user could only fix by clearing the app data). Every other version has a real
        // migration, so a missing migration still fails loudly rather than silently wiping data.
        .fallbackToDestructiveMigrationFrom(true, 1)
        .build()
}