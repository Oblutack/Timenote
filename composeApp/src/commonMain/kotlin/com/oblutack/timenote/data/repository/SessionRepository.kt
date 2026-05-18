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

    private val _deletedTimenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val deletedTimenotes: StateFlow<List<Timenote>> = _deletedTimenotes.asStateFlow()

    private val _deletedFolders = MutableStateFlow<List<com.oblutack.timenote.feature_history.domain.ProjectFolder>>(emptyList())
    val deletedFolders: StateFlow<List<com.oblutack.timenote.feature_history.domain.ProjectFolder>> = _deletedFolders.asStateFlow()

    fun initialize(timenoteDao: TimenoteDao) {
        dao = timenoteDao

        // Listen to Timenotes
        coroutineScope.launch {
            timenoteDao.getAllActiveTimenotes().collect { entityList ->
                _timenotes.value = entityList.map { it.toDomain() }
            }
        }

        // Listen to Folders
        coroutineScope.launch {
            timenoteDao.getAllActiveFolders().collect { entityList ->
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

        coroutineScope.launch {
            timenoteDao.getDeletedTimenotes().collect { entityList ->
                _deletedTimenotes.value = entityList.map { it.toDomain() }
            }
        }
        coroutineScope.launch {
            timenoteDao.getDeletedFolders().collect { entityList ->
                _deletedFolders.value = entityList.map { it.toDomain() }
            }
        }
    }

    fun saveTimenote(timenote: Timenote) {
        coroutineScope.launch {
            dao?.insertTimenote(timenote.toEntity())
        }
    }

    fun deleteTimenote(id: String) {
        coroutineScope.launch { dao?.softDeleteTimenote(id) }
    }

    fun getTimenoteById(id: String): Timenote? {
        return _timenotes.value.find { it.id == id }
    }

    // (If you don't have getFolderById yet, add this quick helper right next to getTimenoteById):
    fun getFolderById(id: String): com.oblutack.timenote.feature_history.domain.ProjectFolder? {
        return _folders.value.find { it.id == id }
    }

    // NEW: Save a Custom Tag to the Database
    fun saveTag(tag: TimenoteFolder) {
        coroutineScope.launch {
            dao?.insertTag(tag.toEntity())
        }
    }

    // NEW: Delete a Custom Tag
    fun deleteTag(id: String) {
        coroutineScope.launch {
            dao?.deleteTag(id)
        }
    }

    fun saveFolder(folder: com.oblutack.timenote.feature_history.domain.ProjectFolder) {
        coroutineScope.launch {
            dao?.insertFolder(folder.toEntity())
        }
    }

    fun deleteFolder(id: String) {
        coroutineScope.launch {
            dao?.softDeleteFolder(id)
            // Optional: If you delete a folder, you might want to un-assign all notes in it!
        }
    }

    // NEW: Update a Timenote's Folder
    fun assignFolderToTimenote(timenoteId: String, folderId: String?) {
        coroutineScope.launch {
            val note = getTimenoteById(timenoteId)
            if (note != null) {
                // Copy the note with the new folder ID and overwrite it in the DB!
                val updatedNote = note.copy(folderId = folderId)
                dao?.insertTimenote(updatedNote.toEntity())
            }
        }
    }
    // NEW: Update a Timenote's Description inline
    fun updateTimenoteDescription(timenoteId: String, newDescription: String) {
        coroutineScope.launch {
            val note = getTimenoteById(timenoteId)
            if (note != null) {
                // Copy the note with the new text and overwrite it in the DB!
                val updatedNote = note.copy(description = newDescription)
                dao?.insertTimenote(updatedNote.toEntity())
            }
        }
    }

    // NEW: Update Title inline
    fun updateTimenoteTitle(timenoteId: String, newTitle: String) {
        coroutineScope.launch {
            val note = getTimenoteById(timenoteId)
            if (note != null) {
                val updatedNote = note.copy(title = newTitle)
                dao?.insertTimenote(updatedNote.toEntity())
            }
        }
    }

    // NEW: Update Tags inline
    fun updateTimenoteTags(timenoteId: String, newTags: List<TimenoteFolder>) {
        coroutineScope.launch {
            val note = getTimenoteById(timenoteId)
            if (note != null) {
                val updatedNote = note.copy(tags = newTags)
                dao?.insertTimenote(updatedNote.toEntity())
            }
        }
    }

    // --- TRASH BIN ACTIONS ---
    fun restoreTimenote(id: String) { coroutineScope.launch { dao?.restoreTimenote(id) } }
    fun hardDeleteTimenote(id: String) { coroutineScope.launch { dao?.hardDeleteTimenote(id) } }

    fun restoreFolder(id: String) { coroutineScope.launch { dao?.restoreFolder(id) } }
    fun hardDeleteFolder(id: String) { coroutineScope.launch { dao?.hardDeleteFolder(id) } }

    fun emptyTrash() {
        coroutineScope.launch {
            _deletedTimenotes.value.forEach { dao?.hardDeleteTimenote(it.id) }
            _deletedFolders.value.forEach { dao?.hardDeleteFolder(it.id) }
        }
    }

    fun toggleFolderPin(id: String) {
        coroutineScope.launch {
            val folder = getFolderById(id) // You might need to add getFolderById similar to getTimenoteById
            if (folder != null) dao?.updateFolderPin(id, !folder.isPinned)
        }
    }

    fun toggleTimenotePin(id: String) {
        coroutineScope.launch {
            val note = getTimenoteById(id)
            if (note != null) dao?.updateTimenotePin(id, !note.isPinned)
        }
    }

}