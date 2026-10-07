package com.oblutack.timenote.sync

import com.oblutack.timenote.core.audioFileName
import com.oblutack.timenote.core.logError
import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.database.SyncStateEntity
import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.drive.ByteSink
import com.oblutack.timenote.drive.ByteSource
import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.drive.RemoteStore
import com.oblutack.timenote.getCurrentTimeMillis

/** The voice memo files on this device (platform specific). Names are plain file names, never paths. */
interface AudioStorage {
    /** Size of the local file, or null if this device does not have it. */
    suspend fun size(name: String): Long?

    suspend fun source(name: String): ByteSource?

    /** A destination for a download; nothing appears under [name] until the sink is finished. */
    suspend fun sink(name: String): ByteSink
}

/** Decides whether the network right now may be used to upload voice memos (the "Wi-Fi only" setting). */
interface AudioNetworkPolicy {
    suspend fun mayUpload(): Boolean
}

sealed class FetchResult {
    /** The memo is on this device now (it already was, or it was downloaded). */
    data object Ready : FetchResult()

    /** The cloud does not have it (never uploaded, or already removed). Nothing can be done about that here. */
    data object NotInCloud : FetchResult()

    /** Could not download it now (offline, signed out...). Trying again later may work. */
    data class Failed(val error: RemoteException) : FetchResult()
}

/** What the playback code needs: make sure a memo is on this device before playing it. */
interface AudioFetcher {
    suspend fun fetch(name: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): FetchResult
}

data class AudioSyncStats(
    val uploaded: Int = 0,
    val failed: Int = 0,
    val removedFromCloud: Int = 0,
    /** What went wrong with the first memo that failed, in words (for logs and the debug panel). */
    val firstError: String? = null
)

/**
 * Voice memos in the cloud. Memos never change after they are recorded, so the rules are simple: upload each one
 * once (when the network policy allows), remember which memos the cloud holds, download one only when someone wants
 * to play it, and remove from the cloud the memos no note refers to any more.
 *
 * The cloud holds the files as "audio/<name>"; what is known about them lives in sync_state under [SyncKind.AUDIO].
 */
class AudioSync(
    private val dao: TimenoteDao,
    private val remote: RemoteStore,
    private val storage: AudioStorage,
    private val policy: AudioNetworkPolicy,
    private val now: () -> Long = ::getCurrentTimeMillis
) : AudioFetcher {

    /** Every memo name any note refers to, trashed notes included (they can be restored). */
    private suspend fun referencedNames(): Set<String> {
        val names = HashSet<String>()
        dao.getAllTimenotesOnce().forEach { entity ->
            val note = entity.toDomain()
            note.voiceNotes.forEach { names += audioFileName(it) }
            note.timelineEvents.forEach { e -> e.audioPath?.let { names += audioFileName(it) } }
        }
        return names
    }

    private suspend fun audioStates(): Map<String, SyncStateEntity> =
        dao.getAllSyncStates().filter { it.entityKind == SyncKind.AUDIO }.associateBy { it.entityId }

    /**
     * Uploads referenced memos the cloud does not have yet, then removes cloud memos nothing refers to.
     * A problem with one memo does not stop the others; problems that need the user (signed out, Drive full)
     * are passed on so the whole sync reports them.
     */
    suspend fun run(): AudioSyncStats {
        var stats = AudioSyncStats()
        val referenced = referencedNames()
        val states = audioStates()

        if (policy.mayUpload()) {
            for (name in referenced.sorted()) {
                if (states[name]?.remoteFileId != null) continue // memos never change: once is enough
                val size = storage.size(name) ?: continue // recorded on another device, or the file is gone
                val source = storage.source(name) ?: continue
                try {
                    val file = remote.uploadFrom(SyncPaths.audio(name), source)
                    dao.upsertSyncState(SyncStateEntity(SyncKind.AUDIO, name, file.id, now(), size.toString(), file.md5))
                    stats = stats.copy(uploaded = stats.uploaded + 1)
                } catch (e: RemoteException) {
                    if (e is RemoteException.NeedsSignIn || e is RemoteException.Unauthorized || e is RemoteException.StorageFull) throw e
                    logError("AudioSync", "Could not upload voice memo $name", e)
                    stats = stats.copy(failed = stats.failed + 1, firstError = stats.firstError ?: "$name: ${e.message}")
                    if (e is RemoteException.Network) break // no connection: the rest would fail the same way
                }
            }
        }

        // Cloud memos no note refers to: the voice note was removed, or its note was deleted for good
        for ((name, state) in states) {
            if (name in referenced) continue
            val fileId = state.remoteFileId ?: continue
            try {
                remote.delete(fileId)
            } catch (e: RemoteException.NotFound) {
                // already gone
            } catch (e: RemoteException) {
                if (e is RemoteException.NeedsSignIn || e is RemoteException.Unauthorized) throw e
                continue // try again next time; the state stays
            }
            dao.deleteSyncState(SyncKind.AUDIO, name)
            stats = stats.copy(removedFromCloud = stats.removedFromCloud + 1)
        }
        return stats
    }

    override suspend fun fetch(name: String, onProgress: (Long, Long) -> Unit): FetchResult {
        if (storage.size(name) != null) return FetchResult.Ready
        val fileId = audioStates()[name]?.remoteFileId ?: findInCloud(name) ?: return FetchResult.NotInCloud
        val sink = storage.sink(name)
        return try {
            remote.downloadTo(fileId, sink, onProgress)
            FetchResult.Ready
        } catch (e: RemoteException.NotFound) {
            dao.deleteSyncState(SyncKind.AUDIO, name)
            FetchResult.NotInCloud
        } catch (e: RemoteException) {
            FetchResult.Failed(e)
        }
    }

    /** The file may exist although no sync recorded it yet (for example a changes feed that has not been read). */
    private suspend fun findInCloud(name: String): String? = try {
        remote.list().find { it.name == SyncPaths.audio(name) }?.id?.also { id ->
            dao.upsertSyncState(SyncStateEntity(SyncKind.AUDIO, name, id, now(), null, null))
        }
    } catch (e: RemoteException) {
        null
    }
}
