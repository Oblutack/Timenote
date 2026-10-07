package com.oblutack.timenote.sync

import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.testutil.FakeCheckpoint
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeRemoteStore
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One device: its own database, repository and sync engine, all talking to the same shared "cloud". */
class Replica(val name: String, scope: CoroutineScope, val cloud: FakeRemoteStore, private val clock: Clock) {
    val dao = FakeTimenoteDao()
    val checkpoint = FakeCheckpoint()
    val repo = SessionRepository(dao, scope, FakeDefaultTagsState(seeded = true), FakeDeviceId(name), now = { clock.now })
    val engine = SyncEngine(dao, cloud, checkpoint, FakeDeviceId(name), repo.writeLock, now = { clock.now })

    suspend fun sync(): SyncResult = engine.sync()

    suspend fun syncDone(): SyncStats = (sync() as SyncResult.Done).stats

    /** What the user sees: titles, text, pin state, tags of every note (also trashed), in a comparable form. */
    suspend fun view(): List<String> = dao.getAllTimenotesOnce().sortedBy { it.id }.map {
        val n = it.toDomain()
        "note ${it.id} title=${n.title} text=${n.description} pinned=${n.isPinned} trashed=${it.isDeleted} folder=${n.folderId} " +
            "tags=${n.tags.map { t -> t.id }.sorted()} voice=${n.voiceNotes}"
    } + dao.getAllFoldersOnce().sortedBy { it.id }.map { "folder ${it.id} name=${it.name} pinned=${it.isPinned} trashed=${it.isDeleted}" } +
        dao.getAllTagsOnce().sortedBy { it.id }.map { "tag ${it.id} name=${it.name} deleted=${it.isDeleted}" }
}

class Clock(var now: Long = 1_000_000L) {
    fun tick(ms: Long = 1_000): Long { now += ms; return now }
}

class SyncEngineTest {
    private fun TestScope.replica(name: String, cloud: FakeRemoteStore, clock: Clock) = Replica(name, backgroundScope, cloud, clock)

    // ---------------------------------------------------------------- basics

    @Test fun whatOneDeviceCreatesAnotherReceives() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTag(testTag("t1", "Focus"))
        a.repo.saveFolder(testFolder("f1", "Writing"))
        a.repo.saveTimenote(testNote("n1", title = "Deep work").copy(folderId = "f1", tags = listOf(testTag("t1", "Focus"))))

        val up = a.syncDone()
        val down = b.syncDone()

        assertEquals(3, up.uploaded)
        assertEquals(3, down.downloaded)
        assertEquals(a.view(), b.view())
        assertEquals(setOf("tags/t1.json", "folders/f1.json", "notes/n1.json"), cloud.snapshot().keys)
    }

    @Test fun syncingAgainChangesNothingAndTransfersNothing() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1"))
        a.syncDone(); b.syncDone()

        assertEquals(SyncStats(), a.syncDone())
        assertEquals(SyncStats(), b.syncDone())
        assertEquals(SyncStats(), a.syncDone())
        assertEquals(a.view(), b.view())
    }

    @Test fun anEditTravelsToTheOtherDeviceAndBack() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1", title = "Old"))
        a.sync(); b.sync()

        clock.tick(); b.repo.updateTimenoteTitle("n1", "Edited on b")
        b.sync(); a.sync()

        assertEquals("Edited on b", a.dao.getAllTimenotesOnce().single().title)
    }

    @Test fun editsToDifferentFieldsOnTwoDevicesBothSurvive() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1", title = "T"))
        a.sync(); b.sync()

        clock.tick(); a.repo.updateTimenoteTitle("n1", "Title from a")
        clock.tick(); b.repo.updateTimenoteDescription("n1", "Text from b")
        b.repo.toggleTimenotePin("n1")
        a.sync(); b.sync(); a.sync()

        listOf(a, b).forEach {
            val n = it.dao.getAllTimenotesOnce().single()
            assertEquals("Title from a", n.title)
            assertEquals("Text from b", n.description)
            assertTrue(n.isPinned)
        }
        assertEquals(a.view(), b.view())
    }

    @Test fun tagsAddedOnDifferentDevicesAreBothKept() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        listOf("t1", "t2").forEach { a.repo.saveTag(testTag(it)) }
        a.repo.saveTimenote(testNote("n1"))
        a.sync(); b.sync()

        clock.tick(); a.repo.updateTimenoteTags("n1", listOf(testTag("t1")))
        clock.tick(); b.repo.updateTimenoteTags("n1", listOf(testTag("t2")))
        a.sync(); b.sync(); a.sync()

        assertEquals(listOf("t1", "t2"), a.dao.getAllTimenotesOnce().single().toDomain().tags.map { it.id }.sorted())
        assertEquals(a.view(), b.view())
    }

    // ---------------------------------------------------------------- deleting

    @Test fun trashAndRestoreTravelBetweenDevices() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1"))
        a.sync(); b.sync()

        clock.tick(); a.repo.deleteTimenote("n1"); a.sync(); b.sync()
        assertTrue(b.dao.getAllTimenotesOnce().single().isDeleted)

        clock.tick(); b.repo.restoreTimenote("n1"); b.sync(); a.sync()
        assertTrue(a.dao.getAllTimenotesOnce().none { it.isDeleted })
    }

    @Test fun aPermanentDeletionReachesTheCloudAndOtherDevices() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.repo.saveTimenote(testNote("keep"))
        a.sync(); b.sync()

        clock.tick(); a.repo.deleteTimenote("n1")
        clock.tick(); a.repo.hardDeleteTimenote("n1")
        val upA = a.syncDone()
        assertEquals(1, upA.deletedRemotely)
        assertTrue("notes/n1.json" !in cloud.snapshot().keys)
        assertTrue(a.dao.getPendingRemoteDeletes().isEmpty(), "the pending deletion is finished")

        val upB = b.syncDone()
        assertEquals(1, upB.removedLocally)
        assertEquals(listOf("keep"), b.dao.getAllTimenotesOnce().map { it.id })
        assertTrue(b.dao.getAllSyncStates().none { it.entityId == "n1" })
    }

    @Test fun aDeletionNeverDestroysACopyThatWasEditedElsewhereAfterTheLastSync() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1", title = "Precious"))
        a.sync(); b.sync()

        clock.tick(); a.repo.deleteTimenote("n1"); a.repo.hardDeleteTimenote("n1")
        a.sync() // the file leaves the cloud
        clock.tick(); b.repo.updateTimenoteDescription("n1", "I wrote more here")
        val result = b.syncDone()

        assertEquals(0, result.removedLocally)
        assertEquals("I wrote more here", b.dao.getAllTimenotesOnce().single().description)
        assertTrue("notes/n1.json" in cloud.snapshot().keys, "the edited copy is uploaded again")
    }

    @Test fun aPermanentlyDeletedItemIsNotBroughtBackByTheCopyStillInTheCloud() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTimenote(testNote("n1"))
        a.sync()
        a.repo.deleteTimenote("n1"); a.repo.hardDeleteTimenote("n1")

        cloud.failNext(RemoteException.Server(503), times = 1) // the very first call of the run fails
        assertIs<SyncResult.Failed>(a.sync())
        assertTrue(a.dao.getAllTimenotesOnce().isEmpty())
        assertEquals(1, a.dao.getPendingRemoteDeletes().size, "still remembered for the next attempt")

        // the cloud still has the file; a normal run must delete it, not download it
        assertEquals(1, a.syncDone().deletedRemotely)
        assertTrue(a.dao.getAllTimenotesOnce().isEmpty())
        assertTrue(cloud.snapshot().isEmpty() || "notes/n1.json" !in cloud.snapshot().keys)
    }

    @Test fun aPendingDeletionWhoseLocalRowStillExistsKeepsTheData() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTimenote(testNote("n1"))
        a.sync()
        // simulate a crash between "remember the deletion" and "delete the row"
        a.dao.insertPendingRemoteDelete(com.oblutack.timenote.data.database.PendingRemoteDeleteEntity(SyncKind.NOTE, "n1", null, 1))

        a.syncDone()

        assertEquals(1, a.dao.getAllTimenotesOnce().size)
        assertTrue("notes/n1.json" in cloud.snapshot().keys, "the cloud copy must survive too")
        assertTrue(a.dao.getPendingRemoteDeletes().isEmpty())
    }

    // ---------------------------------------------------------------- linking

    @Test fun linkingTwoDevicesThatBothAlreadyHaveDataMergesEverything() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("only-a", title = "A's"))
        b.repo.saveTimenote(testNote("only-b", title = "B's"))
        a.repo.saveTimenote(testNote("shared", title = "from a"))
        clock.tick()
        b.repo.saveTimenote(testNote("shared", title = "from b"))

        a.sync(); b.sync(); a.sync()

        assertEquals(a.view(), b.view())
        assertEquals(setOf("only-a", "only-b", "shared"), a.dao.getAllTimenotesOnce().map { it.id }.toSet())
        assertEquals("from b", a.dao.getAllTimenotesOnce().single { it.id == "shared" }.title, "the later write wins")
    }

    // ---------------------------------------------------------------- trouble

    @Test fun aFailedRunKeepsLocalDataAndTheNextRunFinishesTheJob() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        repeat(5) { a.repo.saveTimenote(testNote("n$it")) }
        // calls: 1 startPageToken, 2 list, 3 changes, 4.. uploads: fail the third upload
        cloud.failNext(RemoteException.Server(500), times = 0)
        val flaky = FailOnCall(cloud, failOnCall = 6, error = RemoteException.Network(RuntimeException("offline")))
        val engine = SyncEngine(a.dao, flaky, a.checkpoint, FakeDeviceId("a"), a.repo.writeLock, now = { clock.now })

        assertIs<SyncResult.Failed>(engine.sync())
        assertNull(a.checkpoint.token, "the position in the feed is only saved after a complete run")
        assertEquals(5, a.dao.getAllTimenotesOnce().size)

        assertIs<SyncResult.Done>(engine.sync())
        assertEquals(5, cloud.snapshot().keys.count { it.startsWith("notes/") }, "no duplicates, nothing missing")
        assertNotNull(a.checkpoint.token)
        assertEquals(SyncStats(), engine.syncDone())
    }

    @Test fun lostAccessAsksForSignInInsteadOfLooping() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTimenote(testNote("n1"))
        cloud.failNext(RemoteException.NeedsSignIn())
        assertIs<SyncResult.NeedsSignIn>(a.sync())
        cloud.failNext(RemoteException.Unauthorized())
        assertIs<SyncResult.NeedsSignIn>(a.sync())
        assertEquals(1, a.dao.getAllTimenotesOnce().size)
    }

    @Test fun aFullDriveIsReportedAsSuch() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTimenote(testNote("n1"))
        cloud.failNext(RemoteException.StorageFull())
        assertIs<SyncResult.StorageFull>(a.sync())
    }

    @Test fun twoRunsAtTheSameTimeDoNotCollide() = runAppTest {
        val cloud = FakeRemoteStore().also { it.latencyMs = 100 }; val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTimenote(testNote("n1"))
        val first = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { a.sync() }
        assertIs<SyncResult.AlreadyRunning>(a.sync())
        assertIs<SyncResult.Done>(first.await())
    }

    // ---------------------------------------------------------------- protecting foreign and damaged data

    @Test fun aFileFromANewerAppVersionIsNeverReadOrOverwritten() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        val future = """{"v":9,"id":"n1","something":"new"}"""
        cloud.upload("notes/n1.json", future.encodeToByteArray())
        a.repo.saveTimenote(testNote("n1", title = "Local older"))

        val stats = a.syncDone()
        clock.tick(); a.repo.updateTimenoteTitle("n1", "Local edit")
        a.syncDone()

        assertEquals(1, stats.skipped)
        assertEquals(future, cloud.snapshot().getValue("notes/n1.json").decodeToString(), "the newer file is untouched")
        assertEquals("Local edit", a.dao.getAllTimenotesOnce().single().title)
    }

    @Test fun aDamagedFileIsRepairedByTheNextUpload() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        cloud.upload("notes/n1.json", "{ this is not json".encodeToByteArray())
        a.repo.saveTimenote(testNote("n1", title = "Good copy"))

        val stats = a.syncDone()

        assertEquals(1, stats.skipped)
        val repaired = SyncJson.decodeFromString<SyncNote>(cloud.snapshot().getValue("notes/n1.json").decodeToString())
        assertEquals("Good copy", repaired.fields.title.value)
    }

    @Test fun filesThatAreNotSyncItemsAreIgnored() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        cloud.upload("debug/web-check.json", "{}".encodeToByteArray())
        cloud.upload("notes/", byteArrayOf())
        cloud.upload("notes/a/b.json", byteArrayOf())
        cloud.upload("readme.txt", byteArrayOf())
        assertEquals(SyncStats(), a.syncDone())
    }

    @Test fun twoCloudFilesWithTheSameNameAreMergedIntoOne() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        // two devices created tag "1" independently and uploaded it before seeing each other
        a.repo.saveTag(testTag("1", "Work"))
        a.syncDone()
        val newerCopy = SyncJson.encodeToString(
            SyncTag(
                id = "1",
                fields = SyncTagFields(
                    SyncField("Job", clock.now + 5_000, "b"), SyncField(null, 0, "b"),
                    SyncField("#4FA8F9", 0, "b"), SyncField(null, 0, "b")
                )
            )
        )
        cloud.upload("tags/1.json", newerCopy.encodeToByteArray())
        assertEquals(2, cloud.list().count { it.name == "tags/1.json" })

        val c = replica("c", cloud, clock)
        c.syncDone()

        assertEquals(1, cloud.list().count { it.name == "tags/1.json" }, "the extra file is removed")
        assertEquals("Job", c.dao.getAllTagsOnce().single().name, "the newer name won")
    }

    // ---------------------------------------------------------------- clocks

    @Test fun aTimestampFarInTheFutureIsPulledBackToTheLimit() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock(now = 10_000_000L)
        val a = replica("a", cloud, clock)
        val tenDays = clock.now + 10L * 24 * 60 * 60 * 1000
        val note = SyncNote(
            id = "n1", createdAt = 1,
            fields = SyncNoteFields(
                title = SyncField("From a wrong clock", tenDays, "x"), description = SyncField("", 1, "x"),
                folderId = SyncField(null, 1, "x"), isPinned = SyncField(false, 1, "x"), deletedAt = SyncField(null, 1, "x"),
                parentId = SyncField(null, 1, "x"), parentWaypointId = SyncField(null, 1, "x")
            ),
            tags = emptyMap(), voiceNotes = emptyMap(), session = SyncSession(0, 0, emptyList())
        )
        cloud.upload("notes/n1.json", SyncJson.encodeToString(note).encodeToByteArray())

        a.syncDone()

        val stamp = a.dao.getFieldVersions(SyncKind.NOTE, "n1").single { it.field == "title" }
        assertEquals(clock.now + MAX_CLOCK_SKEW_MS, stamp.updatedAt)
    }
}

/** Wraps a store and fails exactly one numbered call (counting from 1), to cut a sync run in half. */
class FailOnCall(
    private val inner: com.oblutack.timenote.drive.RemoteStore,
    private val failOnCall: Int,
    private val error: RemoteException
) : com.oblutack.timenote.drive.RemoteStore {
    private var calls = 0
    private fun tick() { calls++; if (calls == failOnCall) throw error }
    override suspend fun list() = run { tick(); inner.list() }
    override suspend fun upload(name: String, content: ByteArray, existingId: String?) = run { tick(); inner.upload(name, content, existingId) }
    override suspend fun download(fileId: String) = run { tick(); inner.download(fileId) }
    override suspend fun delete(fileId: String) = run { tick(); inner.delete(fileId) }
    override suspend fun startPageToken() = run { tick(); inner.startPageToken() }
    override suspend fun changes(pageToken: String) = run { tick(); inner.changes(pageToken) }
}

private suspend fun SyncEngine.syncDone(): SyncStats = (sync() as SyncResult.Done).stats
