package com.oblutack.timenote.sync

import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.drive.ByteArraySink
import com.oblutack.timenote.drive.ByteArraySource
import com.oblutack.timenote.drive.ByteSink
import com.oblutack.timenote.drive.ByteSource
import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.testutil.FakeCheckpoint
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeRemoteStore
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testNote
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The voice memo files of one fake device. A download only appears under its name once it is complete. */
class FakeAudioStorage : AudioStorage {
    val files = mutableMapOf<String, ByteArray>()
    var abortedDownloads = 0

    override suspend fun size(name: String) = files[name]?.size?.toLong()
    override suspend fun source(name: String): ByteSource? = files[name]?.let { ByteArraySource(it) }
    override suspend fun sink(name: String): ByteSink = object : ByteSink {
        private val inner = ByteArraySink()
        override suspend fun write(bytes: ByteArray) = inner.write(bytes)
        override suspend fun finish() { files[name] = inner.bytes }
        override suspend fun abort() { abortedDownloads++; inner.abort() }
    }
}

class AllowUploads(var allowed: Boolean = true) : AudioNetworkPolicy {
    override suspend fun mayUpload() = allowed
}

class AudioDevice(val name: String, scope: kotlinx.coroutines.CoroutineScope, val cloud: FakeRemoteStore, clock: Clock) {
    val dao = FakeTimenoteDao()
    val storage = FakeAudioStorage()
    val policy = AllowUploads()
    val repo = SessionRepository(dao, scope, FakeDefaultTagsState(seeded = true), FakeDeviceId(name), now = { clock.now })
    val audio = AudioSync(dao, cloud, storage, policy, now = { clock.now })
    val engine = SyncEngine(dao, cloud, FakeCheckpoint(), FakeDeviceId(name), repo.writeLock, now = { clock.now }, audio = audio)

    suspend fun sync(): SyncResult = engine.sync()
    suspend fun stats(): SyncStats = (sync() as SyncResult.Done).stats

    /** Records a memo the way the app does: the file exists locally and the note lists its name. */
    suspend fun record(noteId: String, memo: String, bytes: ByteArray) {
        storage.files[memo] = bytes
        if (repo.getTimenoteById(noteId) == null) repo.saveTimenote(testNote(noteId))
        repo.addVoiceNote(noteId, memo)
    }
}

class AudioSyncTest {
    private fun TestScope.device(name: String, cloud: FakeRemoteStore, clock: Clock) = AudioDevice(name, backgroundScope, cloud, clock)

    private val memo = ByteArray(2_000) { (it % 251).toByte() }

    @Test fun aNewMemoIsUploadedOnceAndNeverAgain() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock)
        a.record("n1", "memo1.m4a", memo)

        assertEquals(1, a.stats().audioUploaded)
        assertContentEquals(memo, cloud.snapshot().getValue("audio/memo1.m4a"))
        assertEquals(0, a.stats().audioUploaded)
        assertEquals(0, a.stats().audioUploaded)
        assertEquals(1, cloud.snapshot().keys.count { it.startsWith("audio/") })
    }

    @Test fun aMemoRecordedOnOneDevicePlaysOnAnother() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock); val b = device("b", cloud, clock)
        a.record("n1", "memo1.m4a", memo)
        a.sync()

        b.sync()
        assertEquals(listOf("memo1.m4a"), b.repo.getTimenoteById("n1")?.voiceNotes, "the note arrived")
        assertNull(b.storage.size("memo1.m4a"), "the audio is not downloaded until it is needed")

        val progress = mutableListOf<Long>()
        val result = b.audio.fetch("memo1.m4a") { got, _ -> progress += got }

        assertEquals(FetchResult.Ready, result)
        assertContentEquals(memo, b.storage.files.getValue("memo1.m4a"))
        assertEquals(memo.size.toLong(), progress.last())
        assertEquals(FetchResult.Ready, b.audio.fetch("memo1.m4a"), "playing again needs no download")
    }

    @Test fun nothingIsUploadedOnANetworkTheUserDidNotAllow() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock)
        a.record("n1", "memo1.m4a", memo)
        a.policy.allowed = false

        val stats = a.stats()

        assertEquals(0, stats.audioUploaded)
        assertEquals(0, stats.audioFailed, "not allowed is not an error")
        assertTrue(cloud.snapshot().keys.none { it.startsWith("audio/") })
        assertTrue(cloud.snapshot().containsKey("notes/n1.json"), "the notes themselves still sync")

        a.policy.allowed = true
        assertEquals(1, a.stats().audioUploaded)
    }

    @Test fun aMemoThatIsNotInTheCloudIsReportedNotCrashed() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val b = device("b", cloud, clock)
        assertEquals(FetchResult.NotInCloud, b.audio.fetch("never-uploaded.m4a"))
    }

    @Test fun aFailedDownloadLeavesNoHalfWrittenFile() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock); val b = device("b", cloud, clock)
        a.record("n1", "memo1.m4a", memo); a.sync(); b.sync()

        cloud.failNext(RemoteException.Network(RuntimeException("offline")))
        val result = b.audio.fetch("memo1.m4a")

        assertIs<FetchResult.Failed>(result)
        assertNull(b.storage.size("memo1.m4a"))
        // and it works once the connection is back
        assertEquals(FetchResult.Ready, b.audio.fetch("memo1.m4a"))
    }

    @Test fun aMemoWithoutALocalFileIsSimplySkipped() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock); val b = device("b", cloud, clock)
        a.record("n1", "memo1.m4a", memo); a.sync(); b.sync()

        // b has the note but not the file; its syncs must not try to upload a file it does not have
        val stats = b.stats()
        assertEquals(SyncStats(), stats)
        assertEquals(1, cloud.snapshot().keys.count { it.startsWith("audio/") })
    }

    @Test fun anUploadProblemDoesNotStopTheNotesAndIsRetriedNextTime() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock)
        a.record("n1", "memo1.m4a", memo)
        // calls of a first run: startPageToken, list, changes, then 2 uploads (note, ...). Fail the audio upload only.
        val flaky = object : com.oblutack.timenote.drive.RemoteStore by cloud {
            override suspend fun uploadFrom(name: String, source: ByteSource, existingId: String?, onProgress: (Long, Long) -> Unit): com.oblutack.timenote.drive.RemoteFile {
                if (name.startsWith("audio/")) throw RemoteException.Server(500)
                return cloud.uploadFrom(name, source, existingId, onProgress)
            }
        }
        val audio = AudioSync(a.dao, flaky, a.storage, a.policy, now = { clock.now })
        val engine = SyncEngine(a.dao, flaky, FakeCheckpoint(), FakeDeviceId("a"), a.repo.writeLock, now = { clock.now }, audio = audio)

        val first = (engine.sync() as SyncResult.Done).stats
        assertEquals(1, first.audioFailed)
        assertTrue(cloud.snapshot().containsKey("notes/n1.json"))

        assertEquals(1, a.stats().audioUploaded, "a later run with a healthy connection uploads it")
    }

    // ---------------------------------------------------------------- tidying the cloud

    @Test fun aMemoNoNoteRefersToAnyMoreIsRemovedFromTheCloud() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock)
        a.record("n1", "keep.m4a", memo); a.record("n1", "drop.m4a", memo)
        a.sync()
        assertEquals(2, cloud.snapshot().keys.count { it.startsWith("audio/") })

        clock.tick(); a.repo.removeVoiceNote("n1", "drop.m4a")
        val stats = a.stats()

        assertEquals(1, stats.audioRemovedFromCloud)
        assertEquals(setOf("audio/keep.m4a"), cloud.snapshot().keys.filter { it.startsWith("audio/") }.toSet())
    }

    @Test fun aMemoOfATrashedNoteStaysBecauseTheNoteCanBeRestored() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock)
        a.record("n1", "memo1.m4a", memo); a.sync()

        clock.tick(); a.repo.deleteTimenote("n1")
        assertEquals(0, a.stats().audioRemovedFromCloud)
        assertTrue("audio/memo1.m4a" in cloud.snapshot().keys)

        clock.tick(); a.repo.hardDeleteTimenote("n1") // emptied from the trash for good
        assertEquals(1, a.stats().audioRemovedFromCloud)
        assertTrue("audio/memo1.m4a" !in cloud.snapshot().keys)
    }

    @Test fun aMemoUsedByANoteOnAnotherDeviceIsNeverRemoved() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock); val b = device("b", cloud, clock)
        a.record("n1", "memo1.m4a", memo); a.sync(); b.sync()

        // b has no file and never uploads; running its sync many times must not delete anything
        repeat(3) { b.sync() }
        a.sync()

        assertTrue("audio/memo1.m4a" in cloud.snapshot().keys)
    }

    @Test fun aMemoRemovedFromTheCloudIsForgottenOnOtherDevices() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock); val b = device("b", cloud, clock)
        a.record("n1", "memo1.m4a", memo); a.sync(); b.sync()
        assertTrue(b.dao.getAllSyncStates().any { it.entityKind == SyncKind.AUDIO })

        clock.tick(); a.repo.deleteTimenote("n1"); a.repo.hardDeleteTimenote("n1")
        a.sync(); b.sync()

        assertTrue(b.dao.getAllSyncStates().none { it.entityKind == SyncKind.AUDIO })
        assertEquals(FetchResult.NotInCloud, b.audio.fetch("memo1.m4a"))
    }

    @Test fun memosRecordedOnTwoDevicesReachEachOther() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock); val b = device("b", cloud, clock)
        val other = ByteArray(500) { 9 }
        a.record("na", "from-a.m4a", memo)
        b.record("nb", "from-b.m4a", other)

        a.sync(); b.sync(); a.sync()

        assertEquals(FetchResult.Ready, a.audio.fetch("from-b.m4a"))
        assertEquals(FetchResult.Ready, b.audio.fetch("from-a.m4a"))
        assertContentEquals(other, a.storage.files.getValue("from-b.m4a"))
        assertContentEquals(memo, b.storage.files.getValue("from-a.m4a"))
    }

    @Test fun legacyPathsInOldNotesStillFindTheirMemo() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = device("a", cloud, clock)
        a.storage.files["memo1.m4a"] = memo
        // an old note stores a whole path
        a.repo.saveTimenote(testNote("n1").copy(voiceNotes = listOf("/data/user/0/app/files/voice_memos/memo1.m4a")))

        assertEquals(1, a.stats().audioUploaded)
        assertTrue("audio/memo1.m4a" in cloud.snapshot().keys)
    }
}
