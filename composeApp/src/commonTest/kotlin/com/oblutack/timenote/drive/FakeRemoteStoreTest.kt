package com.oblutack.timenote.drive

import com.oblutack.timenote.testutil.FakeRemoteStore
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fake stands in for Google Drive in sync tests, so it has to behave like Drive in the ways sync relies on. */
class FakeRemoteStoreTest {

    @Test fun createReadUpdateDelete() = runTest {
        val store = FakeRemoteStore()
        val file = store.upload("notes/a.json", byteArrayOf(1))
        assertEquals(listOf("notes/a.json"), store.list().map { it.name })
        assertContentEquals(byteArrayOf(1), store.download(file.id))

        store.upload("ignored", byteArrayOf(2, 3), existingId = file.id)
        assertContentEquals(byteArrayOf(2, 3), store.download(file.id))
        assertEquals("notes/a.json", store.list().single().name, "an update keeps the file name")

        store.delete(file.id)
        assertTrue(store.list().isEmpty())
        assertFailsWith<RemoteException.NotFound> { store.download(file.id) }
        assertFailsWith<RemoteException.NotFound> { store.delete(file.id) }
        assertFailsWith<RemoteException.NotFound> { store.upload("x", byteArrayOf(), existingId = "missing") }
    }

    @Test fun theChangesFeedReportsWritesAndDeletesInOrderFromAToken() = runTest {
        val store = FakeRemoteStore()
        val before = store.upload("old.json", byteArrayOf(1))
        val token = store.startPageToken()

        val a = store.upload("a.json", byteArrayOf(1))
        store.upload("a.json", byteArrayOf(2), existingId = a.id)
        store.delete(before.id)

        val page = store.changes(token)
        assertEquals(listOf(a.id, a.id, before.id), page.changes.map { it.fileId })
        assertEquals(listOf(false, false, true), page.changes.map { it.removed })
        assertNull(page.changes[2].file)
        assertNull(page.nextPageToken)
        // reading from the new token finds nothing until something changes again
        assertTrue(store.changes(page.newStartPageToken!!).changes.isEmpty())
    }

    @Test fun largeFeedsArePagedLikeDrive() = runTest {
        val store = FakeRemoteStore(pageSize = 2)
        val token = store.startPageToken()
        repeat(5) { store.upload("f$it", byteArrayOf()) }

        val seen = mutableListOf<String>()
        var next: String? = token
        var pages = 0
        while (next != null) {
            val page = store.changes(next); pages++
            seen += page.changes.map { it.file!!.name }
            next = page.nextPageToken
        }
        assertEquals(listOf("f0", "f1", "f2", "f3", "f4"), seen)
        assertEquals(3, pages)
    }

    @Test fun queuedFailuresHitTheNextCallsThenStop() = runTest {
        val store = FakeRemoteStore()
        store.failNext(RemoteException.RateLimited(), times = 2)
        assertFailsWith<RemoteException.RateLimited> { store.list() }
        assertFailsWith<RemoteException.RateLimited> { store.list() }
        assertTrue(store.list().isEmpty())
        assertEquals(3, store.calls)
    }

    @Test fun latencyTakesVirtualTime() = runTest {
        val store = FakeRemoteStore().also { it.latencyMs = 250 }
        store.list(); store.list()
        assertEquals(500, currentTime)
    }

    // ---------------------------------------------------------------- the self-test used from the debug panel

    @Test fun theSelfTestPassesAgainstAWellBehavedStoreAndCleansUp() = runTest {
        val store = FakeRemoteStore()
        val log = mutableListOf<String>()
        assertTrue(runDriveSelfTest(store) { log += it })
        assertTrue(log.none { it.startsWith("FAIL") }, log.toString())
        assertEquals(8, log.size)
        assertTrue(store.list().isEmpty(), "the test file must not be left behind")
    }

    @Test fun theSelfTestRemovesALeftoverFromAnInterruptedRun() = runTest {
        val store = FakeRemoteStore()
        store.upload("debug/selftest.json", byteArrayOf(9))
        assertTrue(runDriveSelfTest(store) {})
        assertTrue(store.list().isEmpty())
    }

    @Test fun theSelfTestNamesTheStepThatFailed() = runTest {
        val store = FakeRemoteStore()
        val log = mutableListOf<String>()
        val flaky = object : com.oblutack.timenote.drive.RemoteStore by store {
            override suspend fun upload(name: String, content: ByteArray, existingId: String?): RemoteFile =
                throw RemoteException.StorageFull()
        }
        assertFalse(runDriveSelfTest(flaky) { log += it })
        assertTrue(log.last().startsWith("FAIL create file"), log.toString())
        assertTrue(log.last().contains("storage is full"), log.toString())
    }

    // ---------------------------------------------------------------- debug panel helpers

    @Test fun theWebCheckFileIsLeftOnceAndReplacedNotDuplicated() = runTest {
        val store = FakeRemoteStore()
        leaveWebCheckFile(store, now = 1)
        leaveWebCheckFile(store, now = 2)
        val files = store.list()
        assertEquals(listOf(WEB_CHECK_FILE), files.map { it.name })
        assertTrue(store.download(files.single().id).decodeToString().contains(""""at":2"""))
    }

    @Test fun theFolderCanBeDescribedAndCleanedUpWithoutTouchingRealData() = runTest {
        val store = FakeRemoteStore()
        assertTrue(describeAppFolder(store).isEmpty())
        leaveWebCheckFile(store, now = 1)
        store.upload("debug/from-web.json", byteArrayOf())
        store.upload("notes/real.json", byteArrayOf(1, 2, 3))

        assertEquals(3, describeAppFolder(store).size)
        assertEquals(2, deleteDebugFiles(store))
        assertEquals(listOf("notes/real.json (3 bytes)"), describeAppFolder(store))
    }
}
