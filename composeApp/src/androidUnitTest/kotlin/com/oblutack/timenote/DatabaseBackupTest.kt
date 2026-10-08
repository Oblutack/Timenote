package com.oblutack.timenote

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseBackupTest {
    private val dir: File = Files.createTempDirectory("dbs").toFile()
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @AfterTest fun cleanUp() { dir.deleteRecursively() }

    private fun file(name: String, ageDays: Long) = File(dir, name).apply { writeText("x"); setLastModified(now - ageDays * day) }

    @Test fun oldSafetyCopiesAreRemovedAndRecentOnesKept() {
        val oldCopy = file("timenotes.db.bak-v7", 20)
        val oldWal = file("timenotes.db.bak-v7-wal", 20)
        val recent = file("timenotes.db.bak-v6", 3)

        val removed = pruneOldDatabaseBackups(dir, "timenotes.db", now)

        assertEquals(setOf(oldCopy, oldWal), removed.toSet())
        assertTrue(recent.exists())
    }

    @Test fun theRealDatabaseAndOtherFilesAreNeverTouched() {
        val db = file("timenotes.db", 400)
        val wal = file("timenotes.db-wal", 400)
        val shm = file("timenotes.db-shm", 400)
        val other = file("somethingelse.bak-v7", 400)

        assertTrue(pruneOldDatabaseBackups(dir, "timenotes.db", now).isEmpty())

        listOf(db, wal, shm, other).forEach { assertTrue(it.exists(), it.name) }
    }

    @Test fun exactlyTheKeepTimeCountsAsOldAndTheDayBeforeDoesNot() {
        val justOld = file("timenotes.db.bak-v7", 14)
        val justYoung = file("timenotes.db.bak-v6", 13)
        pruneOldDatabaseBackups(dir, "timenotes.db", now)
        assertFalse(justOld.exists())
        assertTrue(justYoung.exists())
    }

    @Test fun aMissingFolderIsNotAnError() {
        assertTrue(pruneOldDatabaseBackups(null, "timenotes.db", now).isEmpty())
        assertTrue(pruneOldDatabaseBackups(File(dir, "nope"), "timenotes.db", now).isEmpty())
    }
}
