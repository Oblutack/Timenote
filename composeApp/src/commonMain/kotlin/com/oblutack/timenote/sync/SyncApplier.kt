package com.oblutack.timenote.sync

import com.oblutack.timenote.data.database.NoteConflictEntity
import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.repository.DeviceIdSource
import com.oblutack.timenote.data.repository.WriteLock
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.getCurrentTimeMillis

enum class ApplyChange { ADDED, UPDATED, UNCHANGED }

/**
 * Decides when the text that loses a merge is kept as an "other version":
 *  - [SYNC]: whichever text loses is kept, unless it is just the old text both sides started from
 *  - [IMPORT]: only a local text that gets replaced by the backup's text is kept (the backup file still holds its own)
 */
enum class ConflictMode { SYNC, IMPORT }

/**
 * What applying a remote item did to the local data.
 * [localEqualsRemote] tells whether, after merging, this device's copy is identical to the remote one. When it is
 * not (this device has changes the remote copy lacks, or the remote copy was damaged by a lost update), the item
 * has to be uploaded again.
 */
data class ApplyOutcome(val change: ApplyChange, val localEqualsRemote: Boolean)

/** A remote timestamp more than this far ahead of our clock is treated as "now": one wrong clock must not win forever. */
const val MAX_CLOCK_SKEW_MS = 24L * 60 * 60 * 1000

/**
 * Merges an item that came from outside (the cloud, or a backup file) into the local database.
 * For each item it reads the CURRENT local copy, merges, and writes, all while holding the [writeLock], so a user
 * edit made at the same moment is never overwritten by a stale copy.
 */
class SyncApplier(
    private val dao: TimenoteDao,
    private val deviceIdSource: DeviceIdSource,
    private val writeLock: WriteLock = WriteLock(),
    private val now: () -> Long = ::getCurrentTimeMillis
) {

    suspend fun applyTag(incoming: SyncTag): ApplyOutcome = writeLock.run {
        val remote = incoming.clampedTo(now() + MAX_CLOCK_SKEW_MS)
        val device = deviceIdSource.deviceId()
        val local = dao.getTagOnce(remote.id)
        val localSync = local?.toSync(dao.getFieldVersions(SyncKind.TAG, remote.id), device)
        val merged = localSync?.let { mergeTags(it, remote) } ?: remote
        val equalsRemote = merged == remote
        if (merged == localSync) return@run ApplyOutcome(ApplyChange.UNCHANGED, equalsRemote)

        val (entity, stamps) = merged.toEntity(sessionCount = local?.sessionCount ?: 0)
        dao.insertTag(entity)
        stamps.forEach { dao.upsertFieldVersion(it) }
        ApplyOutcome(if (local == null) ApplyChange.ADDED else ApplyChange.UPDATED, equalsRemote)
    }

    suspend fun applyFolder(incoming: SyncFolder): ApplyOutcome = writeLock.run {
        val remote = incoming.clampedTo(now() + MAX_CLOCK_SKEW_MS)
        val device = deviceIdSource.deviceId()
        val local = dao.getFolderOnce(remote.id)
        val localSync = local?.toSync(dao.getFieldVersions(SyncKind.FOLDER, remote.id), device)
        val merged = localSync?.let { mergeFolders(it, remote) } ?: remote
        val equalsRemote = merged == remote
        if (merged == localSync) return@run ApplyOutcome(ApplyChange.UNCHANGED, equalsRemote)

        val (entity, stamps) = merged.toEntity()
        dao.insertFolder(entity)
        stamps.forEach { dao.upsertFieldVersion(it) }
        ApplyOutcome(if (local == null) ApplyChange.ADDED else ApplyChange.UPDATED, equalsRemote)
    }

    /**
     * [tagLookup] provides the tag details a note embeds; by default they come from the database, so tags
     * should be applied before the notes that use them.
     * [baseTextHash] is the hash of the note's text as it was when both sides last agreed (null if never):
     * a text that differs from it was changed since, which is what makes a losing text worth keeping.
     */
    suspend fun applyNote(
        incoming: SyncNote,
        tagLookup: ((String) -> TimenoteFolder?)? = null,
        mode: ConflictMode = ConflictMode.IMPORT,
        baseTextHash: String? = null
    ): ApplyOutcome = writeLock.run {
        val remote = incoming.clampedTo(now() + MAX_CLOCK_SKEW_MS)
        val device = deviceIdSource.deviceId()
        val lookup = tagLookup ?: dao.getAllTagsOnce().filter { !it.isDeleted }.associate { it.id to it.toDomain() }.let { m -> { id: String -> m[id] } }
        val local = dao.getTimenoteOnce(remote.id)
        val localSync = local?.toSync(dao.getFieldVersions(SyncKind.NOTE, remote.id), device)
        val merged = localSync?.let { mergeNotes(it, remote) } ?: remote
        val equalsRemote = merged == remote
        if (localSync != null) keepLosingText(localSync, remote, merged, mode, baseTextHash)
        if (merged == localSync) return@run ApplyOutcome(ApplyChange.UNCHANGED, equalsRemote)

        val (entity, stamps) = merged.toEntity(lookup)
        dao.insertTimenote(entity)
        stamps.forEach { dao.upsertFieldVersion(it) }
        ApplyOutcome(if (local == null) ApplyChange.ADDED else ApplyChange.UPDATED, equalsRemote)
    }

    /** Saves the text that lost the merge as an "other version", when it is worth keeping (see [ConflictMode]). */
    private suspend fun keepLosingText(local: SyncNote, remote: SyncNote, merged: SyncNote, mode: ConflictMode, baseTextHash: String?) {
        val localText = local.fields.description
        val remoteText = remote.fields.description
        if (localText.value == remoteText.value) return
        val remoteWins = merged.fields.description.value == remoteText.value
        val loser = if (remoteWins) localText else remoteText
        if (loser.value.isBlank()) return
        val worthKeeping = when (mode) {
            ConflictMode.IMPORT -> remoteWins
            // the old shared text is not a lost edit; anything else the loser says is
            ConflictMode.SYNC -> baseTextHash == null || contentHash(loser.value) != baseTextHash
        }
        if (!worthKeeping) return
        dao.insertConflict(NoteConflictEntity(local.id, contentHash(loser.value), loser.value, loser.t, loser.d, now()))
    }
}

// ---- clock skew: pull every timestamp that lies too far in the future back to the limit ----

private fun <T> SyncField<T>.clamp(limit: Long) = if (t > limit) copy(t = limit) else this
private fun SyncSetEntry.clamp(limit: Long) = if (t > limit) copy(t = limit) else this

fun SyncNote.clampedTo(limit: Long): SyncNote = copy(
    fields = fields.copy(
        title = fields.title.clamp(limit),
        description = fields.description.clamp(limit),
        folderId = fields.folderId.clamp(limit),
        isPinned = fields.isPinned.clamp(limit),
        deletedAt = fields.deletedAt.clamp(limit),
        parentId = fields.parentId.clamp(limit),
        parentWaypointId = fields.parentWaypointId.clamp(limit)
    ),
    tags = tags.mapValues { it.value.clamp(limit) },
    voiceNotes = voiceNotes.mapValues { it.value.clamp(limit) }
)

fun SyncFolder.clampedTo(limit: Long): SyncFolder = copy(
    fields = fields.copy(
        name = fields.name.clamp(limit),
        description = fields.description.clamp(limit),
        color = fields.color.clamp(limit),
        isPinned = fields.isPinned.clamp(limit),
        deletedAt = fields.deletedAt.clamp(limit)
    )
)

fun SyncTag.clampedTo(limit: Long): SyncTag = copy(
    fields = fields.copy(
        name = fields.name.clamp(limit),
        description = fields.description.clamp(limit),
        color = fields.color.clamp(limit),
        deletedAt = fields.deletedAt.clamp(limit)
    )
)
