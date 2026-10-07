package com.oblutack.timenote.sync

import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.database.SyncStateEntity
import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.repository.DeviceIdSource
import com.oblutack.timenote.data.repository.WriteLock
import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.drive.RemoteFile
import com.oblutack.timenote.drive.RemoteStore
import com.oblutack.timenote.getCurrentTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString

data class SyncStats(
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val removedLocally: Int = 0,
    val deletedRemotely: Int = 0,
    /** Cloud files that were left alone: written by a newer app version, or damaged. */
    val skipped: Int = 0
)

sealed class SyncResult {
    data class Done(val stats: SyncStats) : SyncResult()

    /** Access was revoked or never granted. Nothing can be done in the background; ask the user. */
    data object NeedsSignIn : SyncResult()

    /** The user's Google Drive is full. */
    data object StorageFull : SyncResult()

    /** Something temporary or unexpected. Local data is untouched; a later run continues where this one stopped. */
    data class Failed(val error: RemoteException) : SyncResult()

    /** A sync was already in progress. */
    data object AlreadyRunning : SyncResult()
}

/**
 * One sync run: pull what changed in the cloud and merge it in, remove what was permanently deleted here, push what
 * changed here, then read the changes our own pushes caused. Everything is idempotent and nothing is thrown away
 * unless it is certain it was deleted on purpose, so a run that is interrupted anywhere is simply repeated.
 *
 * The engine talks only to [RemoteStore], so it is tested completely against an in-memory fake.
 */
class SyncEngine(
    private val dao: TimenoteDao,
    private val remote: RemoteStore,
    private val checkpoint: SyncCheckpoint,
    private val deviceIdSource: DeviceIdSource,
    writeLock: WriteLock = WriteLock(),
    private val now: () -> Long = ::getCurrentTimeMillis
) {
    private val writeLock = writeLock
    private val applier = SyncApplier(dao, deviceIdSource, writeLock, now)
    private val running = Mutex()

    suspend fun sync(): SyncResult {
        if (!running.tryLock()) return SyncResult.AlreadyRunning
        try {
            return Run().execute()
        } finally {
            running.unlock()
        }
    }

    private inner class Run {
        private var stats = SyncStats()
        private val states = HashMap<Pair<String, String>, SyncStateEntity>()
        private val pendingDeletes = HashSet<Pair<String, String>>()
        private val duplicateFiles = mutableListOf<String>()

        suspend fun execute(): SyncResult = try {
            dao.getAllSyncStates().forEach { states[it.entityKind to it.entityId] = it }
            dao.getPendingRemoteDeletes().forEach { pendingDeletes += it.entityKind to it.entityId }

            var token = checkpoint.pageToken()
            if (token == null) {
                // Linking: remember "now" first, so nothing that happens while we read everything is missed
                token = remote.startPageToken()
                pullEverything()
            }
            token = pullChanges(token)
            deleteRemotelyWhatWasDeletedHere()
            val uploaded = pushLocalChanges()
            if (uploaded > 0) token = pullChanges(token) // sees other devices' writes made meanwhile
            removeDuplicateFiles()
            checkpoint.savePageToken(token) // only now: the run is complete
            SyncResult.Done(stats)
        } catch (e: RemoteException.NeedsSignIn) {
            SyncResult.NeedsSignIn
        } catch (e: RemoteException.Unauthorized) {
            SyncResult.NeedsSignIn
        } catch (e: RemoteException.StorageFull) {
            SyncResult.StorageFull
        } catch (e: RemoteException) {
            SyncResult.Failed(e)
        }

        // ------------------------------------------------------------------ pull

        private suspend fun pullEverything() {
            val byName = remote.list().groupBy { it.name }
            for ((name, files) in byName) {
                val (kind, id) = SyncPaths.parse(name) ?: continue
                // The same name twice (two devices created it at once): merge both, keep one file, delete the rest
                val ordered = files.sortedWith(compareBy({ it.modifiedTime ?: "" }, { it.id }))
                val canonical = ordered.first()
                ordered.forEach { file ->
                    if (file.id != canonical.id) duplicateFiles += file.id
                    downloadAndApply(kind, id, file, isDuplicate = file.id != canonical.id)
                }
            }
        }

        private suspend fun pullChanges(startToken: String): String {
            var next: String? = startToken
            var newStart: String? = null
            while (next != null) {
                val page = remote.changes(next)
                for (change in page.changes) {
                    if (change.removed) handleRemoteRemoval(change.fileId)
                    else change.file?.let { handleChangedFile(it) }
                }
                next = page.nextPageToken
                if (next == null) newStart = page.newStartPageToken
            }
            return newStart ?: startToken
        }

        private suspend fun handleChangedFile(file: RemoteFile) {
            val (kind, id) = SyncPaths.parse(file.name) ?: return
            val state = states[kind to id]
            // Our own upload, or nothing new since we last looked
            if (state != null && state.remoteFileId == file.id && file.md5 != null && state.remoteMd5 == file.md5) return
            val isDuplicate = state?.remoteFileId != null && state.remoteFileId != file.id
            if (isDuplicate) duplicateFiles += file.id
            downloadAndApply(kind, id, file, isDuplicate)
        }

        private suspend fun downloadAndApply(kind: String, id: String, file: RemoteFile, isDuplicate: Boolean) {
            // A permanent deletion made here must not be undone by the copy that is still in the cloud
            if ((kind to id) in pendingDeletes) return

            val text = try {
                remote.download(file.id).decodeToString()
            } catch (e: RemoteException.NotFound) {
                return // gone meanwhile; its removal will show up in the feed
            }

            val version = peekFormatVersion(text)
            if (version == null) {
                // Not even a readable sync file (damaged): leave local data alone; our next upload replaces it
                stats = stats.copy(skipped = stats.skipped + 1)
                saveState(kind, id, file, contentHash = null)
                return
            }
            if (!isSupportedFormat(version)) {
                // Written by a newer app: read nothing, and never overwrite it
                stats = stats.copy(skipped = stats.skipped + 1)
                saveState(kind, id, file, SyncStateEntity.BLOCKED_HASH)
                return
            }

            val outcome = try {
                when (kind) {
                    SyncKind.NOTE -> applier.applyNote(SyncJson.decodeFromString<SyncNote>(text))
                    SyncKind.FOLDER -> applier.applyFolder(SyncJson.decodeFromString<SyncFolder>(text))
                    else -> applier.applyTag(SyncJson.decodeFromString<SyncTag>(text))
                }
            } catch (e: kotlinx.serialization.SerializationException) {
                // Damaged file: leave local data alone; the next upload of the local item replaces it
                stats = stats.copy(skipped = stats.skipped + 1)
                saveState(kind, id, file, contentHash = null)
                return
            }
            if (outcome.change != ApplyChange.UNCHANGED) stats = stats.copy(downloaded = stats.downloaded + 1)

            // If this device now equals the cloud copy nothing needs uploading; otherwise the item is "dirty"
            val sameAsCloud = outcome.localEqualsRemote && !isDuplicate
            saveState(kind, id, file, if (sameAsCloud) serializeLocal(kind, id)?.let(::contentHash) else null)
        }

        private suspend fun handleRemoteRemoval(fileId: String) {
            val state = states.values.find { it.remoteFileId == fileId } ?: return // not ours / already handled
            val key = state.entityKind to state.entityId
            val currentHash = serializeLocal(state.entityKind, state.entityId)?.let(::contentHash)
            val unchangedSinceSync = currentHash != null && currentHash == state.contentHash

            if (currentHash == null) {
                forget(key) // already gone here too
            } else if (unchangedSinceSync && state.entityKind != SyncKind.TAG) {
                // Deleted on another device and not touched here since: follow the deletion
                writeLock.run {
                    when (state.entityKind) {
                        SyncKind.NOTE -> dao.hardDeleteTimenote(state.entityId)
                        SyncKind.FOLDER -> dao.hardDeleteFolder(state.entityId)
                    }
                    dao.deleteFieldVersions(state.entityKind, state.entityId)
                }
                forget(key)
                stats = stats.copy(removedLocally = stats.removedLocally + 1)
            } else {
                // Edited here after the last sync (or a tag): data safety wins, the item is uploaded again as new
                putState(state.copy(remoteFileId = null, contentHash = null, remoteMd5 = null))
            }
        }

        // ------------------------------------------------------------------ push

        private suspend fun deleteRemotelyWhatWasDeletedHere() {
            for (pending in dao.getPendingRemoteDeletes()) {
                val key = pending.entityKind to pending.entityId
                val stillHere = when (pending.entityKind) {
                    SyncKind.NOTE -> dao.getTimenoteOnce(pending.entityId) != null
                    SyncKind.FOLDER -> dao.getFolderOnce(pending.entityId) != null
                    else -> false
                }
                if (stillHere) {
                    // The deletion was recorded but never carried out locally (a crash in between): keep the data
                    dao.deletePendingRemoteDelete(pending.entityKind, pending.entityId)
                    pendingDeletes -= key
                    continue
                }
                val fileId = pending.remoteFileId ?: states[key]?.remoteFileId
                if (fileId != null) {
                    try { remote.delete(fileId) } catch (e: RemoteException.NotFound) { /* already gone */ }
                    stats = stats.copy(deletedRemotely = stats.deletedRemotely + 1)
                }
                forget(key)
                dao.deletePendingRemoteDelete(pending.entityKind, pending.entityId)
                pendingDeletes -= key
            }
        }

        private suspend fun pushLocalChanges(): Int {
            var uploaded = 0
            for ((key, json) in snapshotLocal()) {
                if (key in pendingDeletes) continue
                val (kind, id) = key
                val state = states[key]
                if (state?.contentHash == SyncStateEntity.BLOCKED_HASH) continue
                val hash = contentHash(json)
                if (state?.contentHash == hash) continue

                val bytes = json.encodeToByteArray()
                val name = SyncPaths.of(kind, id)
                val file = try {
                    remote.upload(name, bytes, state?.remoteFileId)
                } catch (e: RemoteException.NotFound) {
                    remote.upload(name, bytes, null) // the file was removed from the cloud: create it again
                }
                putState(SyncStateEntity(kind, id, file.id, now(), hash, file.md5))
                uploaded++
                stats = stats.copy(uploaded = stats.uploaded + 1)
            }
            return uploaded
        }

        private suspend fun removeDuplicateFiles() {
            duplicateFiles.distinct().forEach {
                try { remote.delete(it) } catch (e: RemoteException.NotFound) { /* already gone */ }
            }
            duplicateFiles.clear()
        }

        // ------------------------------------------------------------------ local snapshots and state

        /** Every local item as sync-file text, tags first, then folders, then notes. */
        private suspend fun snapshotLocal(): Map<Pair<String, String>, String> {
            val device = deviceIdSource.deviceId()
            val versions = dao.getAllFieldVersions().groupBy { it.entityKind to it.entityId }
            val result = LinkedHashMap<Pair<String, String>, String>()
            dao.getAllTagsOnce().sortedBy { it.id }.forEach {
                result[SyncKind.TAG to it.id] = SyncJson.encodeToString(it.toSync(versions[SyncKind.TAG to it.id].orEmpty(), device))
            }
            dao.getAllFoldersOnce().sortedBy { it.id }.forEach {
                result[SyncKind.FOLDER to it.id] = SyncJson.encodeToString(it.toSync(versions[SyncKind.FOLDER to it.id].orEmpty(), device))
            }
            dao.getAllTimenotesOnce().sortedBy { it.id }.forEach {
                result[SyncKind.NOTE to it.id] = SyncJson.encodeToString(it.toSync(versions[SyncKind.NOTE to it.id].orEmpty(), device))
            }
            return result
        }

        private suspend fun serializeLocal(kind: String, id: String): String? {
            val device = deviceIdSource.deviceId()
            val versions = dao.getFieldVersions(kind, id)
            return when (kind) {
                SyncKind.NOTE -> dao.getTimenoteOnce(id)?.let { SyncJson.encodeToString(it.toSync(versions, device)) }
                SyncKind.FOLDER -> dao.getFolderOnce(id)?.let { SyncJson.encodeToString(it.toSync(versions, device)) }
                else -> dao.getTagOnce(id)?.let { SyncJson.encodeToString(it.toSync(versions, device)) }
            }
        }

        private suspend fun saveState(kind: String, id: String, file: RemoteFile, contentHash: String?) {
            putState(SyncStateEntity(kind, id, file.id, now(), contentHash, file.md5))
        }

        private suspend fun putState(state: SyncStateEntity) {
            states[state.entityKind to state.entityId] = state
            dao.upsertSyncState(state)
        }

        private suspend fun forget(key: Pair<String, String>) {
            states.remove(key)
            dao.deleteSyncState(key.first, key.second)
        }
    }
}
