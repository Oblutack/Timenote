package com.oblutack.timenote.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimenoteDao {

    // --- TIMENOTES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimenote(timenote: TimenoteEntity)

    // 1. Only get active notes!
    @Query("SELECT * FROM timenotes WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllActiveTimenotes(): Flow<List<TimenoteEntity>>

    // 2. Get the trash!
    @Query("SELECT * FROM timenotes WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedTimenotes(): Flow<List<TimenoteEntity>>

    // 3. "Soft Delete" (Hides it)
    @Query("UPDATE timenotes SET isDeleted = 1, deletedAt = :timestamp, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTimenote(id: String, timestamp: Long)

    // 4. Restore (Pulls it out of trash)
    @Query("UPDATE timenotes SET isDeleted = 0, deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreTimenote(id: String, updatedAt: Long)

    // 5. Hard Delete (For emptying the trash)
    @Query("DELETE FROM timenotes WHERE id = :id")
    suspend fun hardDeleteTimenote(id: String)

    // Targeted column updates: avoid rewriting the whole row from a possibly stale in-memory copy.
    // Every write also stamps updatedAt, which is how sync finds what changed.
    @Query("UPDATE timenotes SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimenoteTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE timenotes SET description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimenoteDescription(id: String, description: String, updatedAt: Long)

    @Query("UPDATE timenotes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimenoteFolder(id: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE timenotes SET tagsJson = :tagsJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimenoteTags(id: String, tagsJson: String, updatedAt: Long)

    @Query("UPDATE timenotes SET voiceNotesJson = :voiceNotesJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimenoteVoiceNotes(id: String, voiceNotesJson: String, updatedAt: Long)

    // Only used by the one-off voice memo migration, which rewrites references without a user edit,
    // so it deliberately leaves updatedAt alone.
    @Query("UPDATE timenotes SET timelineEventsJson = :timelineEventsJson WHERE id = :id")
    suspend fun updateTimenoteTimelineEvents(id: String, timelineEventsJson: String)

    // One-off snapshot (active and trashed) for data migrations
    @Query("SELECT * FROM timenotes")
    suspend fun getAllTimenotesOnce(): List<TimenoteEntity>

    @Query("UPDATE timenotes SET parentTimenoteId = NULL, parentWaypointId = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun orphanTimenote(id: String, updatedAt: Long)

    // --- TAGS ---
    // There is no trash screen for tags, but deleting one is a soft delete so the deletion can reach other devices.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Query("UPDATE tags SET isDeleted = 1, deletedAt = :timestamp, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTag(id: String, timestamp: Long)

    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    // --- PROJECT FOLDERS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    // 1. Only get active folders!
    @Query("SELECT * FROM project_folders WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllActiveFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM project_folders WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedFolders(): Flow<List<FolderEntity>>

    // 2. "Soft Delete" (Hides it)
    @Query("UPDATE project_folders SET isDeleted = 1, deletedAt = :timestamp, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteFolder(id: String, timestamp: Long)

    // 3. Restore
    @Query("UPDATE project_folders SET isDeleted = 0, deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreFolder(id: String, updatedAt: Long)

    // 4. Hard Delete
    @Query("DELETE FROM project_folders WHERE id = :id")
    suspend fun hardDeleteFolder(id: String)

    @Query("UPDATE project_folders SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFolderPin(id: String, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE timenotes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimenotePin(id: String, isPinned: Boolean, updatedAt: Long)

    // --- FULL SNAPSHOTS (backup and sync): active AND trashed/deleted rows ---
    @Query("SELECT * FROM project_folders")
    suspend fun getAllFoldersOnce(): List<FolderEntity>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsOnce(): List<TagEntity>

    @Query("SELECT * FROM field_versions")
    suspend fun getAllFieldVersions(): List<FieldVersionEntity>

    // --- SYNC BOOKKEEPING ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFieldVersion(version: FieldVersionEntity)

    @Query("SELECT * FROM field_versions WHERE entityKind = :kind AND entityId = :id")
    suspend fun getFieldVersions(kind: String, id: String): List<FieldVersionEntity>

    @Query("DELETE FROM field_versions WHERE entityKind = :kind AND entityId = :id")
    suspend fun deleteFieldVersions(kind: String, id: String)

    // Must be written BEFORE the local row is removed, so a permanent deletion is never forgotten
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingRemoteDelete(pending: PendingRemoteDeleteEntity)

    @Query("SELECT * FROM pending_remote_deletes ORDER BY createdAt")
    suspend fun getPendingRemoteDeletes(): List<PendingRemoteDeleteEntity>
}