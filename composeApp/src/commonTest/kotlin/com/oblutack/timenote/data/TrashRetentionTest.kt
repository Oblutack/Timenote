package com.oblutack.timenote.data

import com.oblutack.timenote.core.TRASH_RETENTION_MS
import com.oblutack.timenote.core.describeTrashItem
import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DAY = 24L * 60 * 60 * 1000

private class TrashEnv(scope: TestScope) {
    var clock = 1_800_000_000_000L
    val dao = FakeTimenoteDao()
    val repo = SessionRepository(dao, scope.backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId(), now = { clock })

    fun advanceDays(days: Long) { clock += days * DAY }

    suspend fun noteIds() = dao.getAllTimenotesOnce().map { it.id }.toSet()
    suspend fun folderIds() = dao.getAllFoldersOnce().map { it.id }.toSet()
    suspend fun cloudDeletions() = dao.getPendingRemoteDeletes().map { it.entityKind to it.entityId }.toSet()
}

class TrashRetentionTest {

    @Test fun aSessionInTheTrashForMoreThan30DaysIsRemovedForGood() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("old"))
        env.repo.deleteTimenote("old")
        env.advanceDays(31)

        env.repo.purgeOldTrash()

        assertEquals(emptySet(), env.noteIds())
    }

    @Test fun itsCloudCopyIsRemovedToo() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("old"))
        env.repo.deleteTimenote("old")
        env.advanceDays(31)

        env.repo.purgeOldTrash()

        assertTrue((SyncKind.NOTE to "old") in env.cloudDeletions(), "the deletion must reach the cloud copy")
    }

    @Test fun aSessionInTheTrashForLessThan30DaysStays() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("recent"))
        env.repo.deleteTimenote("recent")
        env.advanceDays(29)

        env.repo.purgeOldTrash()

        assertEquals(setOf("recent"), env.noteIds())
        assertEquals(emptySet(), env.cloudDeletions())
    }

    @Test fun exactlyThirtyDaysStaysAndTheNextMomentGoes() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("edge"))
        env.repo.deleteTimenote("edge")

        env.clock += TRASH_RETENTION_MS
        env.repo.purgeOldTrash()
        assertEquals(setOf("edge"), env.noteIds(), "exactly 30 days is not yet more than 30 days")

        env.clock += 1
        env.repo.purgeOldTrash()
        assertEquals(emptySet(), env.noteIds())
    }

    @Test fun sessionsThatAreNotInTheTrashAreNeverTouchedHoweverOldTheyAre() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("kept", createdAt = 1_000L))
        env.advanceDays(400)

        env.repo.purgeOldTrash()

        assertEquals(setOf("kept"), env.noteIds())
    }

    @Test fun aRestoredSessionIsNotPurgedEvenThoughItWasDeletedLongAgo() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("back"))
        env.repo.deleteTimenote("back")
        env.advanceDays(20)
        env.repo.restoreTimenote("back")
        env.advanceDays(20) // 40 days after the original delete, but only 20 since it was restored... and it is not in the trash

        env.repo.purgeOldTrash()

        assertEquals(setOf("back"), env.noteIds())
    }

    @Test fun aSessionDeletedAgainAfterARestoreStartsItsThirtyDaysAgain() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("again"))
        env.repo.deleteTimenote("again")
        env.advanceDays(25)
        env.repo.restoreTimenote("again")
        env.repo.deleteTimenote("again") // deleted again now
        env.advanceDays(10) // 35 days after the first delete, 10 after the second

        env.repo.purgeOldTrash()

        assertEquals(setOf("again"), env.noteIds())
    }

    @Test fun onlyTheOldOnesGoWhenThereAreSeveral() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("a")); env.repo.saveTimenote(testNote("b")); env.repo.saveTimenote(testNote("live"))
        env.repo.deleteTimenote("a")
        env.advanceDays(20)
        env.repo.deleteTimenote("b")
        env.advanceDays(15) // a: 35 days, b: 15 days

        env.repo.purgeOldTrash()

        assertEquals(setOf("b", "live"), env.noteIds())
    }

    @Test fun foldersFollowTheSameRule() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveFolder(testFolder("oldFolder")); env.repo.saveFolder(testFolder("newFolder")); env.repo.saveFolder(testFolder("liveFolder"))
        env.repo.deleteFolder("oldFolder")
        env.advanceDays(20)
        env.repo.deleteFolder("newFolder")
        env.advanceDays(15)

        env.repo.purgeOldTrash()

        assertEquals(setOf("newFolder", "liveFolder"), env.folderIds())
        assertTrue((SyncKind.FOLDER to "oldFolder") in env.cloudDeletions())
    }

    @Test fun aTrashedRowWithoutADeletionTimeIsNeverRemovedAutomatically() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("legacy"))
        // a row from before deletion times were recorded: in the trash, but nobody knows since when
        val row = env.dao.getTimenoteOnce("legacy")!!
        env.dao.insertTimenote(row.copy(isDeleted = true, deletedAt = null))
        env.advanceDays(500)

        env.repo.purgeOldTrash()

        assertEquals(setOf("legacy"), env.noteIds())
    }

    @Test fun purgingWithAnEmptyTrashChangesNothing() = runAppTest {
        val env = TrashEnv(this)
        env.repo.saveTimenote(testNote("x"))
        env.repo.saveFolder(testFolder("f"))
        env.advanceDays(100)

        env.repo.purgeOldTrash()

        assertEquals(setOf("x"), env.noteIds())
        assertEquals(setOf("f"), env.folderIds())
        assertEquals(emptySet(), env.cloudDeletions())
    }

    // ---- the words shown next to each item

    @Test fun theTrashAgeIsDescribedInPlainWords() {
        val now = 10 * DAY
        assertEquals("Deleted just now · 30 days left", describeTrashItem(now, now - 20_000))
        assertEquals("Deleted 5 min ago · 30 days left", describeTrashItem(now, now - 5 * 60_000))
        assertEquals("Deleted 3 h ago · 30 days left", describeTrashItem(now, now - 3 * 3_600_000))
        assertEquals("Deleted yesterday · 29 days left", describeTrashItem(now, now - DAY - 1_000))
        assertEquals("Deleted 3 days ago · 27 days left", describeTrashItem(now, now - 3 * DAY))
        assertEquals("Deleted 29 days ago · 1 day left", describeTrashItem(now, now - 29 * DAY))
        assertEquals("Deleted 30 days ago · removed soon", describeTrashItem(now, now - 30 * DAY))
        assertEquals("In the trash", describeTrashItem(now, null))
    }

    @Test fun aDeletionTimeInTheFutureIsTreatedAsJustNow() {
        assertEquals("Deleted just now · 30 days left", describeTrashItem(1_000, 5_000))
    }
}
