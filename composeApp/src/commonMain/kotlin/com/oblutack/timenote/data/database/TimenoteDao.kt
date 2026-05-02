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

    @Query("SELECT * FROM timenotes ORDER BY id DESC")
    fun getAllTimenotes(): Flow<List<TimenoteEntity>>

    @Query("DELETE FROM timenotes WHERE id = :id")
    suspend fun deleteTimenote(id: String)

    // --- TAGS (NEW) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    // Load all tags to display on the UI
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    // --- PROJECT FOLDERS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Query("SELECT * FROM project_folders ORDER BY createdAt DESC")
    fun getAllFolders(): kotlinx.coroutines.flow.Flow<List<FolderEntity>>

    @Query("DELETE FROM project_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)
}