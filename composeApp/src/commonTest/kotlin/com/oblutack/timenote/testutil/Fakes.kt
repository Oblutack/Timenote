package com.oblutack.timenote.testutil

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.oblutack.timenote.data.database.FolderEntity
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

/** In-memory TimenoteDao that mirrors the ordering and soft-delete rules of the real queries. */
class FakeTimenoteDao : TimenoteDao {
    private val notes = MutableStateFlow<List<TimenoteEntity>>(emptyList())
    private val tags = MutableStateFlow<List<TagEntity>>(emptyList())
    private val folders = MutableStateFlow<List<FolderEntity>>(emptyList())

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
        updateNote(id) { it.copy(isDeleted = true, deletedAt = timestamp) }
    override suspend fun restoreTimenote(id: String) =
        updateNote(id) { it.copy(isDeleted = false, deletedAt = null) }
    override suspend fun hardDeleteTimenote(id: String) {
        notes.value = notes.value.filter { it.id != id }
    }
    override suspend fun updateTimenoteTitle(id: String, title: String) = updateNote(id) { it.copy(title = title) }
    override suspend fun updateTimenoteDescription(id: String, description: String) =
        updateNote(id) { it.copy(description = description) }
    override suspend fun updateTimenoteFolder(id: String, folderId: String?) =
        updateNote(id) { it.copy(folderId = folderId) }
    override suspend fun updateTimenoteTags(id: String, tagsJson: String) = updateNote(id) { it.copy(tagsJson = tagsJson) }
    override suspend fun updateTimenoteVoiceNotes(id: String, voiceNotesJson: String) =
        updateNote(id) { it.copy(voiceNotesJson = voiceNotesJson) }
    override suspend fun updateTimenoteTimelineEvents(id: String, timelineEventsJson: String) =
        updateNote(id) { it.copy(timelineEventsJson = timelineEventsJson) }
    override suspend fun getAllTimenotesOnce(): List<TimenoteEntity> = notes.value
    override suspend fun orphanTimenote(id: String) =
        updateNote(id) { it.copy(parentTimenoteId = null, parentWaypointId = null) }
    override suspend fun updateTimenotePin(id: String, isPinned: Boolean) = updateNote(id) { it.copy(isPinned = isPinned) }

    // --- tags ---
    override suspend fun insertTag(tag: TagEntity) {
        tags.value = tags.value.filter { it.id != tag.id } + tag
    }
    override suspend fun deleteTag(id: String) {
        tags.value = tags.value.filter { it.id != id }
    }
    override fun getAllTags(): Flow<List<TagEntity>> = tags.map { list -> list.sortedBy { it.name } }

    // --- folders ---
    override suspend fun insertFolder(folder: FolderEntity) {
        folders.value = folders.value.filter { it.id != folder.id } + folder
    }
    override fun getAllActiveFolders(): Flow<List<FolderEntity>> =
        folders.map { list -> list.filter { !it.isDeleted }.sortedByDescending { it.createdAt } }
    override fun getDeletedFolders(): Flow<List<FolderEntity>> =
        folders.map { list -> list.filter { it.isDeleted }.sortedByDescending { it.deletedAt } }
    override suspend fun softDeleteFolder(id: String, timestamp: Long) =
        updateFolder(id) { it.copy(isDeleted = true, deletedAt = timestamp) }
    override suspend fun restoreFolder(id: String) = updateFolder(id) { it.copy(isDeleted = false, deletedAt = null) }
    override suspend fun hardDeleteFolder(id: String) {
        folders.value = folders.value.filter { it.id != id }
    }
    override suspend fun updateFolderPin(id: String, isPinned: Boolean) = updateFolder(id) { it.copy(isPinned = isPinned) }
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

    override fun startRecording(fileName: String) {
        lastFileName = fileName
        recording = true
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
