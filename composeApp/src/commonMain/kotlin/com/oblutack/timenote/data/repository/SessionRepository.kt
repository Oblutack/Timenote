package com.oblutack.timenote.data.repository

import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.database.toEntity
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.mockFolders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SessionRepository {

    private var dao: TimenoteDao? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val _timenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val timenotes: StateFlow<List<Timenote>> = _timenotes.asStateFlow()

    // NEW: StateFlow for our Custom Tags!
    private val _tags = MutableStateFlow<List<TimenoteFolder>>(emptyList())
    val tags: StateFlow<List<TimenoteFolder>> = _tags.asStateFlow()

    private val _folders = MutableStateFlow<List<com.oblutack.timenote.feature_history.domain.ProjectFolder>>(emptyList())
    val folders: StateFlow<List<com.oblutack.timenote.feature_history.domain.ProjectFolder>> = _folders.asStateFlow()

    fun initialize(timenoteDao: TimenoteDao) {
        dao = timenoteDao

        // Listen to Timenotes
        coroutineScope.launch {
            timenoteDao.getAllTimenotes().collect { entityList ->
                _timenotes.value = entityList.map { it.toDomain() }
            }
        }

        // Listen to Folders
        coroutineScope.launch {
            timenoteDao.getAllFolders().collect { entityList ->
                _folders.value = entityList.map { it.toDomain() }
            }
        }

        // NEW: Listen to Tags
        coroutineScope.launch {
            timenoteDao.getAllTags().collect { entityList ->
                val loadedTags = entityList.map { it.toDomain() }

                // Smart UX: If the database has no tags, inject the default ones!
                if (loadedTags.isEmpty()) {
                    mockFolders.forEach { saveTag(it) }
                } else {
                    _tags.value = loadedTags
                }
            }
        }
    }

    fun saveTimenote(timenote: Timenote) {
        coroutineScope.launch {
            dao?.insertTimenote(timenote.toEntity())
        }
    }

    fun deleteTimenote(id: String) {
        coroutineScope.launch {
            dao?.deleteTimenote(id)
        }
    }

    fun getTimenoteById(id: String): Timenote? {
        return _timenotes.value.find { it.id == id }
    }

    // NEW: Save a Custom Tag to the Database
    fun saveTag(tag: TimenoteFolder) {
        coroutineScope.launch {
            dao?.insertTag(tag.toEntity())
        }
    }

    fun saveFolder(folder: com.oblutack.timenote.feature_history.domain.ProjectFolder) {
        coroutineScope.launch {
            dao?.insertFolder(folder.toEntity())
        }
    }
}