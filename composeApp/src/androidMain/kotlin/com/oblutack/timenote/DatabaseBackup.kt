package com.oblutack.timenote

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.oblutack.timenote.core.logError
import java.io.File

/**
 * Keeps a copy of the user's database from just before a schema upgrade.
 *
 * Migrations are tested, but this is the user's only copy of their notes, so the upgrade starts from a safety net:
 * "timenotes.db.bak-v7" is the exact pre-upgrade database (plus "-wal" if it had unmerged changes). To restore,
 * the files would be renamed back over timenotes.db / timenotes.db-wal. An existing backup is never overwritten, so
 * a second failed attempt cannot replace the good copy with a damaged one.
 *
 * Must run BEFORE Room opens the database. Never throws: a backup that cannot be made must not stop the app.
 */
fun backupDatabaseBeforeUpgrade(context: Context, databaseName: String, targetVersion: Int) {
    try {
        val db = context.getDatabasePath(databaseName)
        if (!db.exists()) return

        val currentVersion = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        if (currentVersion <= 0 || currentVersion >= targetVersion) return

        val backup = File(db.path + ".bak-v$currentVersion")
        if (backup.exists()) return

        val wal = File(db.path + "-wal")
        // The write-ahead log is copied as well: it can hold the newest changes that are not in the main file yet
        if (wal.exists() && wal.length() > 0) wal.copyTo(File(backup.path + "-wal"), overwrite = true)
        db.copyTo(backup, overwrite = false)
    } catch (e: Exception) {
        logError("DatabaseBackup", "Could not back up the database before upgrading", e)
    }
}
