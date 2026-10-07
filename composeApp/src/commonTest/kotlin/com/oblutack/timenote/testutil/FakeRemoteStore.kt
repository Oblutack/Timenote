package com.oblutack.timenote.testutil

import com.oblutack.timenote.drive.ChangesPage
import com.oblutack.timenote.drive.RemoteChange
import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.drive.RemoteFile
import com.oblutack.timenote.drive.RemoteStore
import kotlinx.coroutines.delay

/**
 * An in-memory "Google Drive" for tests, with the same observable behaviour as the real one:
 * a flat set of files, and a changes feed that reports every write and delete in order.
 *
 * Problems can be queued with [failNext] (401, 403 quota, 429, timeouts...) and each call can take
 * [latencyMs] of (virtual) time, so sync code can be tested against a misbehaving server.
 */
class FakeRemoteStore(private val pageSize: Int = 1000) : RemoteStore {
    private class Entry(var file: RemoteFile, var content: ByteArray)
    private class Event(val fileId: String, val removed: Boolean)

    private val files = linkedMapOf<String, Entry>()
    private val feed = mutableListOf<Event>()
    private var nextId = 1

    private val failures = ArrayDeque<RemoteException>()
    var latencyMs: Long = 0
    var calls = 0
        private set

    /**
     * Runs just before every remote call, with the call number. Lets a test make ANOTHER device act in the middle of
     * this device's sync run (a real race), for example to overwrite a file between a pull and a push.
     */
    var beforeCall: (suspend (callNumber: Int) -> Unit)? = null

    /** The next remote call (any kind) fails with [error]. Queue several to fail several calls in a row. */
    fun failNext(error: RemoteException, times: Int = 1) = repeat(times) { failures.addLast(error) }

    /** Everything stored, as name to content (for assertions). */
    fun snapshot(): Map<String, ByteArray> = files.values.associate { it.file.name to it.content }

    private suspend fun enter() {
        calls++
        beforeCall?.invoke(calls)
        if (latencyMs > 0) delay(latencyMs)
        failures.removeFirstOrNull()?.let { throw it }
    }

    override suspend fun list(): List<RemoteFile> { enter(); return files.values.map { it.file } }

    override suspend fun upload(name: String, content: ByteArray, existingId: String?): RemoteFile {
        enter()
        val entry = if (existingId != null) {
            files[existingId] ?: throw RemoteException.NotFound()
        } else {
            val id = "file-${nextId++}"
            Entry(RemoteFile(id, name), content).also { files[id] = it }
        }
        entry.content = content
        entry.file = entry.file.copy(name = name.takeIf { existingId == null } ?: entry.file.name, size = content.size.toLong(), md5 = content.contentHashCode().toString(), modifiedTime = "v${feed.size + 1}")
        feed += Event(entry.file.id, removed = false)
        return entry.file
    }

    override suspend fun download(fileId: String): ByteArray {
        enter()
        return (files[fileId] ?: throw RemoteException.NotFound()).content
    }

    override suspend fun delete(fileId: String) {
        enter()
        files.remove(fileId) ?: throw RemoteException.NotFound()
        feed += Event(fileId, removed = true)
    }

    /** The Google account this fake Drive belongs to. */
    var account: String = "fake-account"

    override suspend fun accountId(): String { enter(); return account }

    override suspend fun startPageToken(): String { enter(); return feed.size.toString() }

    override suspend fun changes(pageToken: String): ChangesPage {
        enter()
        val from = pageToken.toIntOrNull() ?: throw RemoteException.Protocol("bad page token")
        val to = minOf(feed.size, from + pageSize)
        val changes = feed.subList(from, to).map { e ->
            RemoteChange(e.fileId, e.removed, if (e.removed) null else files[e.fileId]?.file)
        }
        return if (to < feed.size) ChangesPage(changes, nextPageToken = to.toString(), newStartPageToken = null)
        else ChangesPage(changes, nextPageToken = null, newStartPageToken = feed.size.toString())
    }
}
