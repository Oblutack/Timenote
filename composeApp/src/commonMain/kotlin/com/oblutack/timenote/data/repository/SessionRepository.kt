package com.oblutack.timenote.data.repository

import com.oblutack.timenote.core.descendantIds
import com.oblutack.timenote.data.database.FieldNames
import com.oblutack.timenote.data.database.FieldVersionEntity
import com.oblutack.timenote.data.database.NoteConflictEntity
import com.oblutack.timenote.feature_history.domain.TextConflict
import com.oblutack.timenote.sync.contentHash
import com.oblutack.timenote.data.database.PendingRemoteDeleteEntity
import com.oblutack.timenote.data.database.SyncKind
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The write time given to the default tags: older than every real edit and every pre-tracking row (time 0). */
internal const val DEFAULT_TAG_STAMP = -1L

/**
 * Single source of truth for timenotes, folders and tags.
 *
 * Keeps in-memory StateFlows fed by the DAO's Flows and exposes fire-and-forget write operations.
 * Everything it needs (DAO, coroutine scope, clock) is passed in, so tests can use a fake DAO.
 *
 * Every write also records *when and on which device* each changed field was written (see [FieldVersionEntity]).
 * That bookkeeping is what lets sync merge edits from several devices field by field.
 */
class SessionRepository(
    private val dao: TimenoteDao,
    private val scope: CoroutineScope,
    private val defaultTags: DefaultTagsState,
    private val deviceIdSource: DeviceIdSource,
    private val now: () -> Long = ::getCurrentTimeMillis,
    /** Shared with sync, so edits and sync merges never interleave. */
    val writeLock: WriteLock = WriteLock()
) {

    private val _timenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val timenotes: StateFlow<List<Timenote>> = _timenotes.asStateFlow()

    private val _tags = MutableStateFlow<List<TimenoteFolder>>(emptyList())
    val tags: StateFlow<List<TimenoteFolder>> = _tags.asStateFlow()

    private val _folders = MutableStateFlow<List<ProjectFolder>>(emptyList())
    val folders: StateFlow<List<ProjectFolder>> = _folders.asStateFlow()

    private val _deletedTimenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val deletedTimenotes: StateFlow<List<Timenote>> = _deletedTimenotes.asStateFlow()

    private val _textConflicts = MutableStateFlow<List<TextConflict>>(emptyList())

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
                if (!defaultTags.isSeeded()) {
                    if (loadedTags.isEmpty()) {
                        // First launch: create the default tags once. They are written before the flag is set,
                        // so an interruption in between simply retries instead of leaving the user with none.
                        // Stamped older than anything real (even tags from before change tracking, which count as
                        // time 0), so a tag the user created or edited always wins over a default on any device.
                        mockFolders.forEach { tag ->
                            dao.insertTag(tag.toEntity(updatedAt = 0L))
                            stamp(SyncKind.TAG, tag.id, DEFAULT_TAG_STAMP, FieldNames.TAG_ALL)
                        }
                    }
                    // Tags that already exist come from an older version: nothing to add, just remember it
                    defaultTags.markSeeded()
                    if (loadedTags.isEmpty()) return@collect // the next emission carries the new tags
                }
                _tags.value = loadedTags
            }
        }
        scope.launch {
            dao.getAllConflicts().collect { rows ->
                _textConflicts.value = rows.map { TextConflict(it.noteId, it.textHash, it.text, it.writtenAt, it.deviceId, it.detectedAt) }
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

    /** Records that [fields] of an item were written at [time] by this device. */
    private suspend fun stamp(kind: String, id: String, time: Long, fields: List<String>, present: Boolean = true) {
        val device = deviceIdSource.deviceId()
        fields.forEach { dao.upsertFieldVersion(FieldVersionEntity(kind, id, it, time, device, present)) }
    }

    private val _localEdits = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    /**
     * Emits after every change the USER makes (not after changes that arrive from sync), so sync can follow
     * soon after an edit without reacting to its own work.
     */
    val localEdits: SharedFlow<Unit> = _localEdits.asSharedFlow()

    /** Runs a write in the background while holding the write lock. */
    private fun write(block: suspend () -> Unit) {
        scope.launch {
            writeLock.run(block)
            _localEdits.tryEmit(Unit)
        }
    }

    // --- TIMENOTES ---

    /** Saves a newly recorded timenote. */
    fun saveTimenote(timenote: Timenote) {
        write {
            val time = now()
            dao.insertTimenote(timenote.toEntity(updatedAt = time))
            val fields = FieldNames.NOTE_ALL +
                timenote.tags.map { FieldNames.tag(it.id) } +
                timenote.voiceNotes.map { FieldNames.voice(it) }
            stamp(SyncKind.NOTE, timenote.id, time, fields)
        }
    }

    fun deleteTimenote(id: String) {
        write { softDeleteNote(id, now()) }
    }

    private suspend fun softDeleteNote(id: String, time: Long) {
        dao.softDeleteTimenote(id, time)
        stamp(SyncKind.NOTE, id, time, listOf(FieldNames.DELETED_AT))
    }

    fun getTimenoteById(id: String): Timenote? = _timenotes.value.find { it.id == id }

    /** All children and sub-children (cycle-safe, see core/TimenoteTree.kt). */
    fun getDescendantIds(parentId: String): List<String> = descendantIds(_timenotes.value, parentId)

    /** Deletes the timenote and ALL its descendants (to the trash). */
    fun cascadeSoftDeleteTimenote(id: String) {
        write {
            val descendants = getDescendantIds(id)
            val timestamp = now()
            softDeleteNote(id, timestamp)
            descendants.forEach { childId -> softDeleteNote(childId, timestamp) }
        }
    }

    /** Deletes the timenote (to the trash) and turns its direct children into roots. */
    fun deleteAndOrphanChildren(id: String) {
        write {
            val time = now()
            _timenotes.value.filter { it.parentTimenoteId == id }.forEach { child ->
                dao.orphanTimenote(child.id, time)
                stamp(SyncKind.NOTE, child.id, time, listOf(FieldNames.PARENT_ID, FieldNames.PARENT_WAYPOINT_ID))
            }
            softDeleteNote(id, time)
        }
    }

    fun assignFolderToTimenote(timenoteId: String, folderId: String?) {
        write {
            val time = now()
            dao.updateTimenoteFolder(timenoteId, folderId, time)
            stamp(SyncKind.NOTE, timenoteId, time, listOf(FieldNames.FOLDER_ID))
        }
    }

    fun updateTimenoteDescription(timenoteId: String, newDescription: String) {
        write {
            val time = now()
            dao.updateTimenoteDescription(timenoteId, newDescription, time)
            stamp(SyncKind.NOTE, timenoteId, time, listOf(FieldNames.DESCRIPTION))
        }
    }

    fun updateTimenoteTitle(timenoteId: String, newTitle: String) {
        write {
            val time = now()
            dao.updateTimenoteTitle(timenoteId, newTitle, time)
            stamp(SyncKind.NOTE, timenoteId, time, listOf(FieldNames.TITLE))
        }
    }

    fun updateTimenoteTags(timenoteId: String, newTags: List<TimenoteFolder>) {
        write {
            val time = now()
            val before = getTimenoteById(timenoteId)?.tags?.map { it.id }?.toSet() ?: emptySet()
            val after = newTags.map { it.id }.toSet()
            dao.updateTimenoteTags(timenoteId, Json.encodeToString(newTags), time)
            // Only the tags that were really added or removed are stamped, so a concurrent edit of
            // a different tag on another device is not overwritten
            stamp(SyncKind.NOTE, timenoteId, time, (after - before).map { FieldNames.tag(it) }, present = true)
            stamp(SyncKind.NOTE, timenoteId, time, (before - after).map { FieldNames.tag(it) }, present = false)
        }
    }

    // Voice notes are a JSON list, so append/remove read the current list but only write that column.
    // [ref] is the stored reference (a file name, see core/AudioFiles.kt).
    fun addVoiceNote(timenoteId: String, ref: String) {
        write {
            val note = getTimenoteById(timenoteId) ?: return@write
            val time = now()
            dao.updateTimenoteVoiceNotes(timenoteId, Json.encodeToString(note.voiceNotes + ref), time)
            stamp(SyncKind.NOTE, timenoteId, time, listOf(FieldNames.voice(ref)), present = true)
        }
    }

    fun removeVoiceNote(timenoteId: String, ref: String) {
        write {
            val note = getTimenoteById(timenoteId) ?: return@write
            val time = now()
            dao.updateTimenoteVoiceNotes(timenoteId, Json.encodeToString(note.voiceNotes - ref), time)
            stamp(SyncKind.NOTE, timenoteId, time, listOf(FieldNames.voice(ref)), present = false)
        }
    }

    fun toggleTimenotePin(id: String) {
        write {
            val note = getTimenoteById(id) ?: return@write
            val time = now()
            dao.updateTimenotePin(id, !note.isPinned, time)
            stamp(SyncKind.NOTE, id, time, listOf(FieldNames.IS_PINNED))
        }
    }

    // --- TAGS (no trash screen; deleting is a soft delete so it can reach other devices) ---

    fun saveTag(tag: TimenoteFolder) {
        write {
            val time = now()
            val existing = _tags.value.find { it.id == tag.id }
            dao.insertTag(tag.toEntity(updatedAt = time))
            val changed = if (existing == null) {
                FieldNames.TAG_ALL
            } else {
                buildList {
                    if (existing.name != tag.name) add(FieldNames.NAME)
                    if (existing.description != tag.description) add(FieldNames.DESCRIPTION)
                    if (existing.color != tag.color) add(FieldNames.COLOR)
                }
            }
            stamp(SyncKind.TAG, tag.id, time, changed)
        }
    }

    fun deleteTag(id: String) {
        write {
            val time = now()
            dao.softDeleteTag(id, time)
            stamp(SyncKind.TAG, id, time, listOf(FieldNames.DELETED_AT))
        }
    }

    // --- FOLDERS ---

    fun getFolderById(id: String): ProjectFolder? = _folders.value.find { it.id == id }

    fun saveFolder(folder: ProjectFolder) {
        write {
            val time = now()
            val existing = getFolderById(folder.id)
            dao.insertFolder(folder.toEntity(updatedAt = time))
            val changed = if (existing == null) {
                FieldNames.FOLDER_ALL
            } else {
                buildList {
                    if (existing.name != folder.name) add(FieldNames.NAME)
                    if (existing.description != folder.description) add(FieldNames.DESCRIPTION)
                    if (existing.color != folder.color) add(FieldNames.COLOR)
                    if (existing.isPinned != folder.isPinned) add(FieldNames.IS_PINNED)
                }
            }
            stamp(SyncKind.FOLDER, folder.id, time, changed)
        }
    }

    fun deleteFolder(id: String) {
        write {
            val time = now()
            dao.softDeleteFolder(id, time)
            stamp(SyncKind.FOLDER, id, time, listOf(FieldNames.DELETED_AT))
        }
    }

    fun toggleFolderPin(id: String) {
        write {
            val folder = getFolderById(id) ?: return@write
            val time = now()
            dao.updateFolderPin(id, !folder.isPinned, time)
            stamp(SyncKind.FOLDER, id, time, listOf(FieldNames.IS_PINNED))
        }
    }

    // --- TRASH ---

    fun restoreTimenote(id: String) {
        write {
            val time = now()
            dao.restoreTimenote(id, time)
            stamp(SyncKind.NOTE, id, time, listOf(FieldNames.DELETED_AT))
        }
    }

    fun restoreFolder(id: String) {
        write {
            val time = now()
            dao.restoreFolder(id, time)
            stamp(SyncKind.FOLDER, id, time, listOf(FieldNames.DELETED_AT))
        }
    }

    fun hardDeleteTimenote(id: String) { write { permanentlyDelete(SyncKind.NOTE, id) } }
    fun hardDeleteFolder(id: String) { write { permanentlyDelete(SyncKind.FOLDER, id) } }

    fun emptyTrash() {
        write {
            _deletedTimenotes.value.forEach { permanentlyDelete(SyncKind.NOTE, it.id) }
            _deletedFolders.value.forEach { permanentlyDelete(SyncKind.FOLDER, it.id) }
        }
    }

    /**
     * Removes an item for good. The fact that its cloud copy must be removed too is written FIRST, so a
     * crash between the two steps can never leave a deletion that is forgotten (see PendingRemoteDeleteEntity).
     */
    private suspend fun permanentlyDelete(kind: String, id: String) {
        dao.insertPendingRemoteDelete(PendingRemoteDeleteEntity(kind, id, remoteFileId = null, createdAt = now()))
        when (kind) {
            SyncKind.NOTE -> { dao.hardDeleteTimenote(id); dao.deleteConflictsFor(id) }
            SyncKind.FOLDER -> dao.hardDeleteFolder(id)
        }
        dao.deleteFieldVersions(kind, id)
    }

    // --- OTHER VERSIONS OF A NOTE'S TEXT (see sync/SyncApplier.kt) ---

    /** Texts that lost a merge between devices, newest first. Empty for almost everybody. */
    val textConflicts: StateFlow<List<TextConflict>> = _textConflicts.asStateFlow()

    fun conflictsFor(noteId: String): List<TextConflict> = _textConflicts.value.filter { it.noteId == noteId }

    /**
     * Brings an "other version" back as the note's text. The text that is replaced is kept as an other version in its
     * place, so switching back and forth never loses anything.
     */
    fun restoreTextConflict(noteId: String, textHash: String) {
        write {
            val conflict = dao.getConflictsFor(noteId).find { it.textHash == textHash } ?: return@write
            val note = getTimenoteById(noteId) ?: return@write
            val time = now()
            if (note.description.isNotBlank() && note.description != conflict.text) {
                dao.insertConflict(
                    NoteConflictEntity(noteId, contentHash(note.description), note.description, time, deviceIdSource.deviceId(), time)
                )
            }
            dao.deleteConflict(noteId, textHash)
            dao.updateTimenoteDescription(noteId, conflict.text, time)
            stamp(SyncKind.NOTE, noteId, time, listOf(FieldNames.DESCRIPTION))
        }
    }

    /** The user does not want this other version any more. */
    fun dismissTextConflict(noteId: String, textHash: String) {
        write { dao.deleteConflict(noteId, textHash) }
    }
}
