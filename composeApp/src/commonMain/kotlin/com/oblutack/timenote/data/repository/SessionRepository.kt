package com.oblutack.timenote.data.repository

import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.database.toEntity
import com.oblutack.timenote.feature_history.domain.Timenote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SessionRepository {

    private var dao: TimenoteDao? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    // We start with an empty list instead of mock data!
    private val _timenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val timenotes: StateFlow<List<Timenote>> = _timenotes.asStateFlow()

    // We call this when the App launches to connect the Database
    fun initialize(timenoteDao: TimenoteDao) {
        dao = timenoteDao

        // This listens to the database FOREVER.
        // If the database changes, the UI updates instantly!
        coroutineScope.launch {
            timenoteDao.getAllTimenotes().collect { entityList ->
                _timenotes.value = entityList.map { it.toDomain() }
            }
        }
    }

    fun saveTimenote(timenote: Timenote) {
        coroutineScope.launch {
            // Convert to Entity and Save to Hard Drive!
            dao?.insertTimenote(timenote.toEntity())
        }
    }

    fun getTimenoteById(id: String): Timenote? {
        return _timenotes.value.find { it.id == id }
    }
}