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
    @Query("UPDATE timenotes SET isDeleted = 1, deletedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTimenote(id: String, timestamp: Long)

    // 4. Restore (Pulls it out of trash)
    @Query("UPDATE timenotes SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreTimenote(id: String)

    // 5. Hard Delete (For emptying the trash)
    @Query("DELETE FROM timenotes WHERE id = :id")
    suspend fun hardDeleteTimenote(id: String)

    // --- TAGS ---
    // (We keep tag deletion permanent, no need for a trash bin for tags)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    @Query("SELECT * FROM tags ORDER BY name ASC")
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
    @Query("UPDATE project_folders SET isDeleted = 1, deletedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteFolder(id: String, timestamp: Long)

    // 3. Restore
    @Query("UPDATE project_folders SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreFolder(id: String)

    // 4. Hard Delete
    @Query("DELETE FROM project_folders WHERE id = :id")
    suspend fun hardDeleteFolder(id: String)

    @Query("UPDATE project_folders SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateFolderPin(id: String, isPinned: Boolean)

    @Query("UPDATE timenotes SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateTimenotePin(id: String, isPinned: Boolean)
}