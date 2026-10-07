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
    /** Old deletion records that were cleaned up (see [TOMBSTONE_TTL_MS]). */
    val compacted: Int = 0,
    val audioUploaded: Int = 0,
    /** Voice memos that could not be uploaded this time (they are tried again next run). */
    val audioFailed: Int = 0,
    val audioRemovedFromCloud: Int = 0,
    /** Why the first voice memo upload failed, if one did. */
    val audioError: String? = null,
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

    /**
     * The signed-in Google account is not the one this device has been syncing with. Nothing was touched. The user
     * decides: go back to [linked], or call [SyncEngine.switchAccount] to merge this device's data into [current].
     */
    data class AccountChanged(val linked: String, val current: String) : SyncResult()
}

enum class LinkKind {
    /** Nothing here and nothing in the cloud yet. */
    NothingYet,
    /** This device has data, the cloud has none: it will be backed up. */
    Backup,
    /** The cloud has data, this device has none: it will be restored. */
    Restore,
    /** Both have data: items with the same id are merged, everything else is added; nothing is deleted. */
    Merge
}

/** The counts shown to the user before a device is linked, so a first sync never comes as a surprise. */
data class LinkPreview(val localNotes: Int, val localFolders: Int, val cloudNotes: Int, val cloudFolders: Int) {
    val kind: LinkKind
        get() {
            val local = localNotes + localFolders > 0
            val cloud = cloudNotes + cloudFolders > 0
            return when {
                local && cloud -> LinkKind.Merge
                local -> LinkKind.Backup
                cloud -> LinkKind.Restore
                else -> LinkKind.NothingYet
            }
        }
}

/** How long a deletion record is kept before it is cleaned up: a device offline for longer may bring the item back. */
const val TOMBSTONE_TTL_MS = 180L * 24 * 60 * 60 * 1000

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
    private val now: () -> Long = ::getCurrentTimeMillis,
    /** Identifies the signed-in Google account; null when the platform cannot tell (the guard is then off). */
    private val accountId: suspend () -> String? = { null },
    /** Voice memos; null when audio is not synced. */
    private val audio: AudioSync? = null
) {
    private val writeLock = writeLock
    private val applier = SyncApplier(dao, deviceIdSource, writeLock, now)
    private val running = Mutex()

    suspend fun sync(): SyncResult {
        if (!running.tryLock()) return SyncResult.AlreadyRunning
        try {
            val current = try { accountId() } catch (e: RemoteException) { return resultFor(e) }
            val linked = checkpoint.linkedAccount()
            if (current != null && linked != null && current != linked) return SyncResult.AccountChanged(linked, current)
            if (current != null && linked == null) checkpoint.saveLinkedAccount(current)
            return Run().execute()
        } finally {
            running.unlock()
        }
    }

    private fun resultFor(e: RemoteException): SyncResult = when (e) {
        is RemoteException.NeedsSignIn, is RemoteException.Unauthorized -> SyncResult.NeedsSignIn
        is RemoteException.StorageFull -> SyncResult.StorageFull
        else -> SyncResult.Failed(e)
    }

    /**
     * Stops being linked to any account: forgets the cloud file ids, unfinished cloud deletions, the changes-feed
     * position and the linked account. Nothing local is touched, and nothing is deleted in the cloud. Connecting
     * again later (to any account) starts with a merge, exactly like the first time.
     */
    suspend fun unlink() {
        running.lock()
        try {
            dao.clearSyncStates()
            dao.clearPendingRemoteDeletes()
            checkpoint.savePageToken(null)
            checkpoint.saveLinkedAccount(null)
        } finally {
            running.unlock()
        }
    }

    /** What connecting this device would do, worked out without changing anything (see [LinkPreview]). */
    suspend fun previewLinking(): LinkPreview {
        val names = remote.list().mapNotNull { SyncPaths.parse(it.name)?.first }
        return LinkPreview(
            localNotes = dao.getAllTimenotesOnce().size,
            localFolders = dao.getAllFoldersOnce().size,
            cloudNotes = names.count { it == SyncKind.NOTE },
            cloudFolders = names.count { it == SyncKind.FOLDER }
        )
    }

    /**
     * The user chose to sync with the currently signed-in account instead of the linked one. Nothing local is deleted:
     * the old account's file ids and unfinished deletions are forgotten, and the next sync merges this device's data
     * with whatever the new account already holds.
     */
    suspend fun switchAccount() {
        val current = try { accountId() } catch (e: RemoteException) { null } ?: return
        running.lock()
        try {
            dao.clearSyncStates()
            dao.clearPendingRemoteDeletes()
            checkpoint.savePageToken(null)
            checkpoint.saveLinkedAccount(current)
        } finally {
            running.unlock()
        }
    }

    private inner class Run {
        private var stats = SyncStats()
        private val states = HashMap<Pair<String, String>, SyncStateEntity>()
        private val pendingDeletes = HashSet<Pair<String, String>>()
        private val duplicateFiles = mutableListOf<String>()
        private val startedAt = now()

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
            syncVoiceMemos()
            removeDuplicateFiles()
            compactOldDeletionRecords()
            checkpoint.savePageToken(token) // only now: the run is complete
            SyncResult.Done(stats)
        } catch (e: RemoteException) {
            resultFor(e)
        }

        // ------------------------------------------------------------------ pull

        private suspend fun pullEverything() {
            val byName = remote.list().groupBy { it.name }
            for ((name, files) in byName) {
                val memo = SyncPaths.parseAudio(name)
                if (memo != null) { noteVoiceMemo(memo, files.first()); continue }
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

        /** Voice memos are only noted here (which cloud file holds which memo); they are downloaded when played. */
        private suspend fun noteVoiceMemo(name: String, file: RemoteFile) {
            val known = states[SyncKind.AUDIO to name]
            if (known != null && known.remoteFileId == file.id) return
            putState(SyncStateEntity(SyncKind.AUDIO, name, file.id, now(), null, file.md5))
        }

        private suspend fun syncVoiceMemos() {
            val result = audio?.run() ?: return
            stats = stats.copy(
                audioUploaded = stats.audioUploaded + result.uploaded,
                audioFailed = stats.audioFailed + result.failed,
                audioRemovedFromCloud = stats.audioRemovedFromCloud + result.removedFromCloud,
                audioError = stats.audioError ?: result.firstError
            )
        }

        private suspend fun handleChangedFile(file: RemoteFile) {
            SyncPaths.parseAudio(file.name)?.let { noteVoiceMemo(it, file); return }
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

            var remoteTextHash: String? = null
            val outcome = try {
                when (kind) {
                    SyncKind.NOTE -> {
                        val note = SyncJson.decodeFromString<SyncNote>(text)
                        remoteTextHash = contentHash(note.fields.description.value)
                        applier.applyNote(note, mode = ConflictMode.SYNC, baseTextHash = states[kind to id]?.baseTextHash)
                    }
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
            saveState(kind, id, file, if (sameAsCloud) serializeLocal(kind, id)?.let(::contentHash) else null, remoteTextHash)
        }

        private suspend fun handleRemoteRemoval(fileId: String) {
            val state = states.values.find { it.remoteFileId == fileId } ?: return // not ours / already handled
            val key = state.entityKind to state.entityId
            if (state.entityKind == SyncKind.AUDIO) { forget(key); return } // the memo left the cloud; the local file stays
            val currentHash = serializeLocal(state.entityKind, state.entityId)?.let(::contentHash)
            val unchangedSinceSync = currentHash != null && currentHash == state.contentHash

            if (currentHash == null) {
                forget(key) // already gone here too
            } else if (unchangedSinceSync) {
                // Deleted on another device (or cleaned up long after it was deleted) and not touched here since:
                // follow the deletion
                writeLock.run {
                    when (state.entityKind) {
                        SyncKind.NOTE -> dao.hardDeleteTimenote(state.entityId)
                        SyncKind.FOLDER -> dao.hardDeleteFolder(state.entityId)
                        else -> dao.hardDeleteTag(state.entityId)
                    }
                    dao.deleteFieldVersions(state.entityKind, state.entityId)
                    dao.deleteConflictsFor(state.entityId)
                }
                forget(key)
                stats = stats.copy(removedLocally = stats.removedLocally + 1)
            } else {
                // Edited here after the last sync: data safety wins, the item is uploaded again as new
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
            // the text each note has right now: what the cloud will hold after this upload
            val baseText = dao.getAllTimenotesOnce().associate { (SyncKind.NOTE to it.id) to contentHash(it.description) }
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
                putState(SyncStateEntity(kind, id, file.id, now(), hash, file.md5, baseText[key]))
                uploaded++
                stats = stats.copy(uploaded = stats.uploaded + 1)
            }
            return uploaded
        }

        /**
         * Cleans up deletion records older than [TOMBSTONE_TTL_MS]: tags that were deleted long ago (and whose deletion
         * has reached the cloud) are removed for good, and so are old "this tag/memo was removed from the note" marks.
         */
        private suspend fun compactOldDeletionRecords() {
            val cutoff = now() - TOMBSTONE_TTL_MS
            dao.deleteRemovedMembershipsOlderThan(cutoff)
            for (tag in dao.getAllTagsOnce()) {
                val deletedAt = tag.deletedAt
                if (!tag.isDeleted || deletedAt == null || deletedAt >= cutoff) continue
                val key = SyncKind.TAG to tag.id
                val state = states[key] ?: continue
                // Other devices must have had a whole run to see the deletion first: not one that was just uploaded
                if (state.syncedAt >= startedAt) continue
                val inCloud = state.contentHash != null && state.contentHash == serializeLocal(key.first, key.second)?.let(::contentHash)
                if (!inCloud) continue // the deletion itself has not been uploaded yet: keep the record until it has
                state.remoteFileId?.let {
                    try { remote.delete(it) } catch (e: RemoteException.NotFound) { /* already gone */ }
                }
                writeLock.run {
                    dao.hardDeleteTag(tag.id)
                    dao.deleteFieldVersions(SyncKind.TAG, tag.id)
                }
                forget(key)
                stats = stats.copy(compacted = stats.compacted + 1)
            }
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

        private suspend fun saveState(kind: String, id: String, file: RemoteFile, contentHash: String?, baseTextHash: String? = null) {
            putState(SyncStateEntity(kind, id, file.id, now(), contentHash, file.md5, baseTextHash))
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
