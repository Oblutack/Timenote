package com.oblutack.timenote.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// This holds all our SQL Queries
@Dao
interface TimenoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimenote(timenote: TimenoteEntity)

    // Flow automatically updates the UI whenever the database changes!
    @Query("SELECT * FROM timenotes ORDER BY id DESC")
    fun getAllTimenotes(): Flow<List<TimenoteEntity>>

    @Query("DELETE FROM timenotes WHERE id = :id")
    suspend fun deleteTimenote(id: String)
}