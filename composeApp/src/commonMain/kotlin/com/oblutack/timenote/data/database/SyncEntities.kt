package com.oblutack.timenote.data.database

import androidx.room.Entity

/** The kinds of things that sync, as used in [FieldVersionEntity.entityKind] and friends. */
object SyncKind {
    const val NOTE = "note"
    const val FOLDER = "folder"
    const val TAG = "tag"
}

/**
 * When, and on which device, one field of a note, folder or tag was last written.
 *
 * Sync merges field by field (the newest write per field wins), so it needs more than a single "updatedAt" per row.
 * For membership lists the [field] is "tag:<tagId>" or "voice:<fileName>" and [present] says whether the element is
 * currently in the list (false = removed, kept so the removal can reach other devices).
 *
 * Rows that existed before this table (created by older versions) simply have no entries; sync falls back to the
 * row's own `updatedAt` for them.
 */
@Entity(tableName = "field_versions", primaryKeys = ["entityKind", "entityId", "field"])
data class FieldVersionEntity(
    val entityKind: String,
    val entityId: String,
    val field: String,
    val updatedAt: Long,
    val deviceId: String,
    val present: Boolean = true
)

/**
 * What sync knows about the cloud copy of each item.
 *  - [remoteFileId]: the Drive file for this item (null when it has none yet)
 *  - [contentHash]: hash of the LOCAL item as last sent to / found equal to the cloud copy; the item needs uploading
 *    when its current hash differs, or when this is null. [BLOCKED_HASH] means the cloud copy was written by a newer
 *    app version and must not be overwritten.
 *  - [remoteMd5]: Drive's checksum of the cloud file as last seen, so our own uploads are not downloaded again
 *  - [syncedAt]: when this item last took part in a sync (local clock)
 */
@Entity(tableName = "sync_state", primaryKeys = ["entityKind", "entityId"])
data class SyncStateEntity(
    val entityKind: String,
    val entityId: String,
    val remoteFileId: String?,
    val syncedAt: Long,
    val contentHash: String?,
    val remoteMd5: String? = null
) {
    companion object {
        const val BLOCKED_HASH = "blocked-newer-format"
    }
}

/**
 * Items that were permanently deleted here and whose cloud copy still has to be removed.
 * Written *before* the local row is deleted, so the deletion can never be forgotten.
 */
@Entity(tableName = "pending_remote_deletes", primaryKeys = ["entityKind", "entityId"])
data class PendingRemoteDeleteEntity(
    val entityKind: String,
    val entityId: String,
    val remoteFileId: String?,
    val createdAt: Long
)

/** Names used in [FieldVersionEntity.field]. They match the field names of the sync file format. */
object FieldNames {
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val FOLDER_ID = "folderId"
    const val IS_PINNED = "isPinned"
    const val DELETED_AT = "deletedAt"
    const val PARENT_ID = "parentId"
    const val PARENT_WAYPOINT_ID = "parentWaypointId"
    const val NAME = "name"
    const val COLOR = "color"

    /** Membership of a tag in a note. */
    fun tag(tagId: String) = "tag:$tagId"

    /** Membership of a voice memo (by file name) in a note. */
    fun voice(ref: String) = "voice:$ref"

    val NOTE_ALL = listOf(TITLE, DESCRIPTION, FOLDER_ID, IS_PINNED, DELETED_AT, PARENT_ID, PARENT_WAYPOINT_ID)
    val FOLDER_ALL = listOf(NAME, DESCRIPTION, COLOR, IS_PINNED, DELETED_AT)
    val TAG_ALL = listOf(NAME, DESCRIPTION, COLOR, DELETED_AT)
}
