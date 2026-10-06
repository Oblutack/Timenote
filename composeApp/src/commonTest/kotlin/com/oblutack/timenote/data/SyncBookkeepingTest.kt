package com.oblutack.timenote.data

import com.oblutack.timenote.data.database.FieldNames
import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sync merges edits field by field, so every write must leave a trace of what changed, when and where.
 * These tests pin that bookkeeping down (over the fake DAO, no database needed).
 */
class SyncBookkeepingTest {
    private var clock = 10_000L

    private fun TestScope.repo(dao: FakeTimenoteDao) =
        SessionRepository(dao, backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId("dev-1"), now = { clock })

    private fun FakeTimenoteDao.version(kind: String, id: String, field: String) =
        fieldVersions.singleOrNull { it.entityKind == kind && it.entityId == id && it.field == field }

    @Test fun aNewNoteStampsEveryFieldIncludingItsTagsAndVoiceNotes() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTimenote(testNote("a").copy(tags = listOf(testTag("t1")), voiceNotes = listOf("m.m4a")))

        FieldNames.NOTE_ALL.forEach { field ->
            val v = assertNotNull(dao.version(SyncKind.NOTE, "a", field), "missing $field")
            assertEquals(10_000L, v.updatedAt)
            assertEquals("dev-1", v.deviceId)
        }
        assertNotNull(dao.version(SyncKind.NOTE, "a", FieldNames.tag("t1")))
        assertNotNull(dao.version(SyncKind.NOTE, "a", FieldNames.voice("m.m4a")))
        assertEquals(10_000L, dao.getAllTimenotesOnce().single().updatedAt)
    }

    @Test fun editingOneFieldStampsOnlyThatField() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTimenote(testNote("a"))

        clock = 20_000L
        repo.updateTimenoteTitle("a", "New title")

        assertEquals(20_000L, dao.version(SyncKind.NOTE, "a", FieldNames.TITLE)!!.updatedAt)
        assertEquals(10_000L, dao.version(SyncKind.NOTE, "a", FieldNames.DESCRIPTION)!!.updatedAt, "untouched field keeps its old stamp")
        assertEquals(20_000L, dao.getAllTimenotesOnce().single().updatedAt)
    }

    @Test fun changingTagsStampsOnlyAddedAndRemovedOnes() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTimenote(testNote("a").copy(tags = listOf(testTag("keep"), testTag("drop"))))

        clock = 20_000L
        repo.updateTimenoteTags("a", listOf(testTag("keep"), testTag("new")))

        assertEquals(10_000L, dao.version(SyncKind.NOTE, "a", FieldNames.tag("keep"))!!.updatedAt)
        assertTrue(dao.version(SyncKind.NOTE, "a", FieldNames.tag("new"))!!.present)
        val dropped = dao.version(SyncKind.NOTE, "a", FieldNames.tag("drop"))!!
        assertEquals(20_000L, dropped.updatedAt)
        assertEquals(false, dropped.present, "a removal is recorded so it can reach other devices")
    }

    @Test fun voiceNoteAddAndRemoveAreRecordedAsMembership() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTimenote(testNote("a"))
        repo.addVoiceNote("a", "m.m4a")
        assertTrue(dao.version(SyncKind.NOTE, "a", FieldNames.voice("m.m4a"))!!.present)

        clock = 30_000L
        repo.removeVoiceNote("a", "m.m4a")
        val removed = dao.version(SyncKind.NOTE, "a", FieldNames.voice("m.m4a"))!!
        assertEquals(false, removed.present)
        assertEquals(30_000L, removed.updatedAt)
    }

    @Test fun trashingAndRestoringStampTheDeletedAtField() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTimenote(testNote("a"))

        clock = 20_000L
        repo.deleteTimenote("a")
        assertEquals(20_000L, dao.version(SyncKind.NOTE, "a", FieldNames.DELETED_AT)!!.updatedAt)
        assertEquals(20_000L, dao.getAllTimenotesOnce().single().updatedAt)

        clock = 30_000L
        repo.restoreTimenote("a")
        assertEquals(30_000L, dao.version(SyncKind.NOTE, "a", FieldNames.DELETED_AT)!!.updatedAt)
        assertEquals(30_000L, dao.getAllTimenotesOnce().single().updatedAt)
    }

    @Test fun orphaningChildrenStampsTheirParentLinks() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTimenote(testNote("parent"))
        repo.saveTimenote(testNote("child", "parent"))

        clock = 20_000L
        repo.deleteAndOrphanChildren("parent")

        assertEquals(20_000L, dao.version(SyncKind.NOTE, "child", FieldNames.PARENT_ID)!!.updatedAt)
        assertEquals(20_000L, dao.version(SyncKind.NOTE, "child", FieldNames.PARENT_WAYPOINT_ID)!!.updatedAt)
    }

    @Test fun folderEditsStampOnlyWhatChanged() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveFolder(testFolder("f", "Writing"))
        assertEquals(FieldNames.FOLDER_ALL.toSet(), dao.fieldVersions.filter { it.entityId == "f" }.map { it.field }.toSet())

        clock = 20_000L
        repo.saveFolder(repo.getFolderById("f")!!.copy(name = "Writing 2"))

        assertEquals(20_000L, dao.version(SyncKind.FOLDER, "f", FieldNames.NAME)!!.updatedAt)
        assertEquals(10_000L, dao.version(SyncKind.FOLDER, "f", FieldNames.COLOR)!!.updatedAt)
    }

    @Test fun deletingATagKeepsATombstoneInsteadOfRemovingTheRow() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTag(testTag("t", "Temp"))

        clock = 20_000L
        repo.deleteTag("t")

        assertTrue(repo.tags.value.none { it.id == "t" }, "hidden from the app")
        val row = dao.allTagRows().single { it.id == "t" }
        assertTrue(row.isDeleted)
        assertEquals(20_000L, row.deletedAt)
        assertEquals(20_000L, dao.version(SyncKind.TAG, "t", FieldNames.DELETED_AT)!!.updatedAt)
    }

    @Test fun savingATagAgainBringsItBackFromTheTombstone() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTag(testTag("t", "Temp"))
        repo.deleteTag("t")
        repo.saveTag(testTag("t", "Temp again"))

        assertEquals(listOf("Temp again"), repo.tags.value.map { it.name })
        assertNull(dao.allTagRows().single().deletedAt)
    }

    @Test fun permanentDeleteRecordsTheRemoteDeletionBeforeRemovingTheRow() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTimenote(testNote("a"))
        repo.deleteTimenote("a")

        repo.hardDeleteTimenote("a")

        val pending = dao.pendingRemoteDeletes.single()
        assertEquals(SyncKind.NOTE, pending.entityKind)
        assertEquals("a", pending.entityId)
        assertEquals(listOf(listOf(pending)), dao.pendingAtHardDelete, "the pending delete existed when the row was deleted")
        assertTrue(dao.fieldVersions.none { it.entityId == "a" }, "no stale per-field data left behind")
    }

    @Test fun emptyingTheTrashRecordsEveryRemoteDeletion() = runAppTest {
        val dao = FakeTimenoteDao()
        val repo = repo(dao)
        repo.saveTimenote(testNote("a")); repo.saveFolder(testFolder("f"))
        repo.deleteTimenote("a"); repo.deleteFolder("f")

        repo.emptyTrash()

        assertEquals(setOf(SyncKind.NOTE to "a", SyncKind.FOLDER to "f"), dao.pendingRemoteDeletes.map { it.entityKind to it.entityId }.toSet())
    }

    @Test fun defaultTagsAreStampedOlderThanAnyRealEditSoRealEditsWin() = runAppTest {
        val dao = FakeTimenoteDao()
        SessionRepository(dao, backgroundScope, FakeDefaultTagsState(), FakeDeviceId(), now = { clock })

        val defaults = dao.fieldVersions.filter { it.entityKind == SyncKind.TAG }
        assertTrue(defaults.isNotEmpty())
        assertTrue(defaults.all { it.updatedAt < 0L }, "older than legacy rows, which count as time 0")
    }
}
