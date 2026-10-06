package com.oblutack.timenote.testutil

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.oblutack.timenote.data.database.FolderEntity
import com.oblutack.timenote.data.repository.DefaultTagsState
import com.oblutack.timenote.data.database.TagEntity
import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.database.TimenoteEntity
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_timer.domain.AudioPlayer
import com.oblutack.timenote.feature_timer.domain.AudioRecorder
import com.oblutack.timenote.feature_timer.domain.TimerServiceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.oblutack.timenote.data.database.FieldVersionEntity
import com.oblutack.timenote.data.database.PendingRemoteDeleteEntity
import com.oblutack.timenote.data.repository.DeviceIdSource

/** In-memory TimenoteDao that mirrors the ordering and soft-delete rules of the real queries. */
class FakeTimenoteDao : TimenoteDao {
    private val notes = MutableStateFlow<List<TimenoteEntity>>(emptyList())
    private val tags = MutableStateFlow<List<TagEntity>>(emptyList())
    private val folders = MutableStateFlow<List<FolderEntity>>(emptyList())
    val fieldVersions = mutableListOf<FieldVersionEntity>()
    val pendingRemoteDeletes = mutableListOf<PendingRemoteDeleteEntity>()
    /** Everything that was still in the table at the moment each hard delete ran, to check ordering. */
    val pendingAtHardDelete = mutableListOf<List<PendingRemoteDeleteEntity>>()

    private fun updateNote(id: String, change: (TimenoteEntity) -> TimenoteEntity) {
        notes.value = notes.value.map { if (it.id == id) change(it) else it }
    }

    private fun updateFolder(id: String, change: (FolderEntity) -> FolderEntity) {
        folders.value = folders.value.map { if (it.id == id) change(it) else it }
    }

    // --- timenotes ---
    override suspend fun insertTimenote(timenote: TimenoteEntity) {
        notes.value = notes.value.filter { it.id != timenote.id } + timenote
    }
    override fun getAllActiveTimenotes(): Flow<List<TimenoteEntity>> =
        notes.map { list -> list.filter { !it.isDeleted }.sortedByDescending { it.createdAt } }
    override fun getDeletedTimenotes(): Flow<List<TimenoteEntity>> =
        notes.map { list -> list.filter { it.isDeleted }.sortedByDescending { it.deletedAt } }
    override suspend fun softDeleteTimenote(id: String, timestamp: Long) =
        updateNote(id) { it.copy(isDeleted = true, deletedAt = timestamp, updatedAt = timestamp) }
    override suspend fun restoreTimenote(id: String, updatedAt: Long) =
        updateNote(id) { it.copy(isDeleted = false, deletedAt = null, updatedAt = updatedAt) }
    override suspend fun hardDeleteTimenote(id: String) {
        pendingAtHardDelete += pendingRemoteDeletes.toList()
        notes.value = notes.value.filter { it.id != id }
    }
    override suspend fun updateTimenoteTitle(id: String, title: String, updatedAt: Long) =
        updateNote(id) { it.copy(title = title, updatedAt = updatedAt) }
    override suspend fun updateTimenoteDescription(id: String, description: String, updatedAt: Long) =
        updateNote(id) { it.copy(description = description, updatedAt = updatedAt) }
    override suspend fun updateTimenoteFolder(id: String, folderId: String?, updatedAt: Long) =
        updateNote(id) { it.copy(folderId = folderId, updatedAt = updatedAt) }
    override suspend fun updateTimenoteTags(id: String, tagsJson: String, updatedAt: Long) =
        updateNote(id) { it.copy(tagsJson = tagsJson, updatedAt = updatedAt) }
    override suspend fun updateTimenoteVoiceNotes(id: String, voiceNotesJson: String, updatedAt: Long) =
        updateNote(id) { it.copy(voiceNotesJson = voiceNotesJson, updatedAt = updatedAt) }
    override suspend fun updateTimenoteTimelineEvents(id: String, timelineEventsJson: String) =
        updateNote(id) { it.copy(timelineEventsJson = timelineEventsJson) }
    override suspend fun getAllTimenotesOnce(): List<TimenoteEntity> = notes.value
    override suspend fun orphanTimenote(id: String, updatedAt: Long) =
        updateNote(id) { it.copy(parentTimenoteId = null, parentWaypointId = null, updatedAt = updatedAt) }
    override suspend fun updateTimenotePin(id: String, isPinned: Boolean, updatedAt: Long) =
        updateNote(id) { it.copy(isPinned = isPinned, updatedAt = updatedAt) }

    // --- tags ---
    override suspend fun insertTag(tag: TagEntity) {
        tags.value = tags.value.filter { it.id != tag.id } + tag
    }
    override suspend fun softDeleteTag(id: String, timestamp: Long) {
        tags.value = tags.value.map { if (it.id == id) it.copy(isDeleted = true, deletedAt = timestamp, updatedAt = timestamp) else it }
    }
    override fun getAllTags(): Flow<List<TagEntity>> = tags.map { list -> list.filter { !it.isDeleted }.sortedBy { it.name } }
    /** Includes soft-deleted tags (the real table keeps them as tombstones). */
    fun allTagRows(): List<TagEntity> = tags.value

    // --- folders ---
    override suspend fun insertFolder(folder: FolderEntity) {
        folders.value = folders.value.filter { it.id != folder.id } + folder
    }
    override fun getAllActiveFolders(): Flow<List<FolderEntity>> =
        folders.map { list -> list.filter { !it.isDeleted }.sortedByDescending { it.createdAt } }
    override fun getDeletedFolders(): Flow<List<FolderEntity>> =
        folders.map { list -> list.filter { it.isDeleted }.sortedByDescending { it.deletedAt } }
    override suspend fun softDeleteFolder(id: String, timestamp: Long) =
        updateFolder(id) { it.copy(isDeleted = true, deletedAt = timestamp, updatedAt = timestamp) }
    override suspend fun restoreFolder(id: String, updatedAt: Long) =
        updateFolder(id) { it.copy(isDeleted = false, deletedAt = null, updatedAt = updatedAt) }
    override suspend fun hardDeleteFolder(id: String) {
        pendingAtHardDelete += pendingRemoteDeletes.toList()
        folders.value = folders.value.filter { it.id != id }
    }
    override suspend fun updateFolderPin(id: String, isPinned: Boolean, updatedAt: Long) =
        updateFolder(id) { it.copy(isPinned = isPinned, updatedAt = updatedAt) }

    // --- sync bookkeeping ---
    override suspend fun upsertFieldVersion(version: FieldVersionEntity) {
        fieldVersions.removeAll { it.entityKind == version.entityKind && it.entityId == version.entityId && it.field == version.field }
        fieldVersions += version
    }
    override suspend fun getFieldVersions(kind: String, id: String) =
        fieldVersions.filter { it.entityKind == kind && it.entityId == id }
    override suspend fun deleteFieldVersions(kind: String, id: String) {
        fieldVersions.removeAll { it.entityKind == kind && it.entityId == id }
    }
    override suspend fun insertPendingRemoteDelete(pending: PendingRemoteDeleteEntity) {
        pendingRemoteDeletes.removeAll { it.entityKind == pending.entityKind && it.entityId == pending.entityId }
        pendingRemoteDeletes += pending
    }
    override suspend fun getPendingRemoteDeletes() = pendingRemoteDeletes.toList()
}

/** In-memory DataStore: applies transforms immediately, no files involved. */
class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** In-memory stand-in for the persisted "default tags were already created" flag. */
class FakeDeviceId(private val id: String = "test-device") : DeviceIdSource {
    override suspend fun deviceId() = id
}

class FakeDefaultTagsState(var seeded: Boolean = false) : DefaultTagsState {
    override suspend fun isSeeded() = seeded
    override suspend fun markSeeded() { seeded = true }
}

class FakeTimerServiceManager : TimerServiceManager {
    data class Notification(val title: String, val timeText: String, val baseMillis: Long, val isPaused: Boolean)

    var startCalls = 0
    var stopCalls = 0
    val notifications = mutableListOf<Notification>()

    override fun startService() { startCalls++ }
    override fun stopService() { stopCalls++ }
    override fun updateNotification(title: String, timeText: String, baseMillis: Long, isPaused: Boolean) {
        notifications += Notification(title, timeText, baseMillis, isPaused)
    }
}

class FakeAudioRecorder : AudioRecorder {
    var lastFileName: String? = null
    var recording = false
    /** Set to false to simulate a missing microphone permission. */
    var canRecord = true

    override fun startRecording(fileName: String): Boolean {
        if (!canRecord) return false
        lastFileName = fileName
        recording = true
        return true
    }
    override fun stopRecording(): String? {
        recording = false
        return lastFileName?.let { "/fake/$it.m4a" }
    }
}

class FakeAudioPlayer : AudioPlayer {
    var playing = false
    var lastPlayed: String? = null
    private var onComplete: () -> Unit = {}

    override fun play(filePath: String, onComplete: () -> Unit) {
        lastPlayed = filePath
        this.onComplete = onComplete
        playing = true
    }
    override fun pause() { playing = false }
    override fun stop() { playing = false }
    override fun isPlaying() = playing

    fun finishPlayback() {
        playing = false
        onComplete()
    }
}

// --- builders ---

fun testNote(
    id: String,
    parent: String? = null,
    createdAt: Long = 1_000L,
    title: String = id,
    activeSeconds: Int = 0,
    pauseSeconds: Int = 0
) = Timenote(
    id = id,
    title = title,
    description = "",
    duration = "00:00:00",
    activeSeconds = activeSeconds,
    pauseSeconds = pauseSeconds,
    createdAt = createdAt,
    tags = emptyList(),
    timelineEvents = emptyList(),
    parentTimenoteId = parent
)

fun testTag(id: String, name: String = id) = TimenoteFolder(id, name, null, 0, Color(0xFF4FA8F9))

fun testFolder(id: String, name: String = id, createdAt: Long = 1_000L) =
    ProjectFolder(id = id, name = name, color = Color(0xFF4CAF50), createdAt = createdAt)
