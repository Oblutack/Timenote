package com.oblutack.timenote.data.repository

import com.oblutack.timenote.core.descendantIds
import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.database.toEntity
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.mockFolders
import com.oblutack.timenote.getCurrentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Single source of truth for timenotes, folders and tags.
 *
 * Keeps in-memory StateFlows fed by the DAO's Flows and exposes fire-and-forget write operations.
 * Everything it needs (DAO, coroutine scope, clock) is passed in, so tests can use a fake DAO.
 */
class SessionRepository(
    private val dao: TimenoteDao,
    private val scope: CoroutineScope,
    private val now: () -> Long = ::getCurrentTimeMillis
) {

    private val _timenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val timenotes: StateFlow<List<Timenote>> = _timenotes.asStateFlow()

    private val _tags = MutableStateFlow<List<TimenoteFolder>>(emptyList())
    val tags: StateFlow<List<TimenoteFolder>> = _tags.asStateFlow()

    private val _folders = MutableStateFlow<List<ProjectFolder>>(emptyList())
    val folders: StateFlow<List<ProjectFolder>> = _folders.asStateFlow()

    private val _deletedTimenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val deletedTimenotes: StateFlow<List<Timenote>> = _deletedTimenotes.asStateFlow()

    private val _deletedFolders = MutableStateFlow<List<ProjectFolder>>(emptyList())
    val deletedFolders: StateFlow<List<ProjectFolder>> = _deletedFolders.asStateFlow()

    init {
        scope.launch {
            dao.getAllActiveTimenotes().collect { entities ->
                _timenotes.value = entities.map { it.toDomain() }
            }
        }
        scope.launch {
            dao.getAllActiveFolders().collect { entities ->
                _folders.value = entities.map { it.toDomain() }
            }
        }
        scope.launch {
            dao.getAllTags().collect { entities ->
                val loadedTags = entities.map { it.toDomain() }
                // First launch: seed the default tags
                if (loadedTags.isEmpty()) {
                    mockFolders.forEach { saveTag(it) }
                } else {
                    _tags.value = loadedTags
                }
            }
        }
        scope.launch {
            dao.getDeletedTimenotes().collect { entities ->
                _deletedTimenotes.value = entities.map { it.toDomain() }
            }
        }
        scope.launch {
            dao.getDeletedFolders().collect { entities ->
                _deletedFolders.value = entities.map { it.toDomain() }
            }
        }
    }

    // --- TIMENOTES ---

    fun saveTimenote(timenote: Timenote) {
        scope.launch { dao.insertTimenote(timenote.toEntity()) }
    }

    fun deleteTimenote(id: String) {
        scope.launch { dao.softDeleteTimenote(id, now()) }
    }

    fun getTimenoteById(id: String): Timenote? = _timenotes.value.find { it.id == id }

    /** All children and sub-children (cycle-safe, see core/TimenoteTree.kt). */
    fun getDescendantIds(parentId: String): List<String> = descendantIds(_timenotes.value, parentId)

    /** Deletes the timenote and ALL its descendants (to the trash). */
    fun cascadeSoftDeleteTimenote(id: String) {
        scope.launch {
            val descendants = getDescendantIds(id)
            val timestamp = now()
            dao.softDeleteTimenote(id, timestamp)
            descendants.forEach { childId -> dao.softDeleteTimenote(childId, timestamp) }
        }
    }

    /** Deletes the timenote (to the trash) and turns its direct children into roots. */
    fun deleteAndOrphanChildren(id: String) {
        scope.launch {
            _timenotes.value.filter { it.parentTimenoteId == id }.forEach { child -> dao.orphanTimenote(child.id) }
            dao.softDeleteTimenote(id, now())
        }
    }

    fun assignFolderToTimenote(timenoteId: String, folderId: String?) {
        scope.launch { dao.updateTimenoteFolder(timenoteId, folderId) }
    }

    fun updateTimenoteDescription(timenoteId: String, newDescription: String) {
        scope.launch { dao.updateTimenoteDescription(timenoteId, newDescription) }
    }

    fun updateTimenoteTitle(timenoteId: String, newTitle: String) {
        scope.launch { dao.updateTimenoteTitle(timenoteId, newTitle) }
    }

    fun updateTimenoteTags(timenoteId: String, newTags: List<TimenoteFolder>) {
        scope.launch { dao.updateTimenoteTags(timenoteId, Json.encodeToString(newTags)) }
    }

    // Voice notes are a JSON list, so append/remove read the current list but only write that column
    fun addVoiceNote(timenoteId: String, path: String) {
        scope.launch {
            val note = getTimenoteById(timenoteId) ?: return@launch
            dao.updateTimenoteVoiceNotes(timenoteId, Json.encodeToString(note.voiceNotes + path))
        }
    }

    fun removeVoiceNote(timenoteId: String, path: String) {
        scope.launch {
            val note = getTimenoteById(timenoteId) ?: return@launch
            dao.updateTimenoteVoiceNotes(timenoteId, Json.encodeToString(note.voiceNotes - path))
        }
    }

    fun toggleTimenotePin(id: String) {
        scope.launch {
            val note = getTimenoteById(id) ?: return@launch
            dao.updateTimenotePin(id, !note.isPinned)
        }
    }

    // --- TAGS (deleted permanently, no trash) ---

    fun saveTag(tag: TimenoteFolder) {
        scope.launch { dao.insertTag(tag.toEntity()) }
    }

    fun deleteTag(id: String) {
        scope.launch { dao.deleteTag(id) }
    }

    // --- FOLDERS ---

    fun getFolderById(id: String): ProjectFolder? = _folders.value.find { it.id == id }

    fun saveFolder(folder: ProjectFolder) {
        scope.launch { dao.insertFolder(folder.toEntity()) }
    }

    fun deleteFolder(id: String) {
        scope.launch { dao.softDeleteFolder(id, now()) }
    }

    fun toggleFolderPin(id: String) {
        scope.launch {
            val folder = getFolderById(id) ?: return@launch
            dao.updateFolderPin(id, !folder.isPinned)
        }
    }

    // --- TRASH ---

    fun restoreTimenote(id: String) { scope.launch { dao.restoreTimenote(id) } }
    fun hardDeleteTimenote(id: String) { scope.launch { dao.hardDeleteTimenote(id) } }

    fun restoreFolder(id: String) { scope.launch { dao.restoreFolder(id) } }
    fun hardDeleteFolder(id: String) { scope.launch { dao.hardDeleteFolder(id) } }

    fun emptyTrash() {
        scope.launch {
            _deletedTimenotes.value.forEach { dao.hardDeleteTimenote(it.id) }
            _deletedFolders.value.forEach { dao.hardDeleteFolder(it.id) }
        }
    }
}
