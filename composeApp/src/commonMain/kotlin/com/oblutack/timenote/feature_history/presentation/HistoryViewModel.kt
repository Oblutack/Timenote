package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.datetime.toLocalDateTime

enum class SortOption(val displayName: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    LONGEST("Longest Duration"),
    SHORTEST("Shortest Duration")
}

class HistoryViewModel : ViewModel() {
    val sessions = SessionRepository.timenotes

    val heatmapData: StateFlow<Map<String, Int>> = sessions.map { allSessions ->
        val map = mutableMapOf<String, Int>()
        allSessions.forEach { session ->
            // Safely convert the timestamp to a local date string
            val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(
                if (session.createdAt > 0L) session.createdAt else com.oblutack.timenote.getCurrentTimeMillis()
            )
            val dateStr = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()

            // Add this session's active time to that day's total
            map[dateStr] = (map[dateStr] ?: 0) + session.activeSeconds
        }
        map
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyMap())

    val folders = SessionRepository.folders
    val tags = SessionRepository.tags

    // --- FILTER & SORT STATE ---
    private val _selectedFilterTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilterTags = _selectedFilterTags.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NEWEST)
    val sortOption = _sortOption.asStateFlow()

    fun toggleFilterTag(tagId: String) {
        val current = _selectedFilterTags.value.toMutableSet()
        if (current.contains(tagId)) current.remove(tagId) else current.add(tagId)
        _selectedFilterTags.value = current
    }

    fun clearTagFilters() {
        _selectedFilterTags.value = emptySet()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    // NEW: Trigger the deletion!
    fun deleteTimenote(id: String) {
        SessionRepository.deleteTimenote(id)
    }

    fun saveFolder(id: String? = null, name: String, description: String? = null, color: Color) { // <-- NEW PARAM
        val currentTime = com.oblutack.timenote.getCurrentTimeMillis()
        val folderToSave = if (id == null) {
            ProjectFolder(
                id = currentTime.toString(),
                name = name,
                description = description, // <-- PASSED IN
                color = color,
                createdAt = currentTime
            )
        } else {
            val existing = folders.value.find { it.id == id }
            ProjectFolder(
                id = id,
                name = name,
                description = description, // <-- PASSED IN
                color = color,
                createdAt = existing?.createdAt ?: currentTime
            )
        }
        SessionRepository.saveFolder(folderToSave)
    }

    fun deleteFolder(id: String) {
        // We need to implement SessionRepository.deleteFolder first but let's assume it exists or will be added
        SessionRepository.deleteFolder(id)
    }

    // --- AUDIO PLAYER STATE ---
    private val _playingAudioPath = MutableStateFlow<String?>(null)
    val playingAudioPath = _playingAudioPath.asStateFlow()
    private val _recordingTimenoteId = MutableStateFlow<String?>(null)
    val recordingTimenoteId = _recordingTimenoteId.asStateFlow()
    fun playAudio(filePath: String) {
        val player = com.oblutack.timenote.feature_timer.domain.AudioLocator.audioPlayer

        if (_playingAudioPath.value == filePath && player?.isPlaying() == true) {
            player.pause()
            _playingAudioPath.value = null
        } else {
            player?.play(filePath) {
                _playingAudioPath.value = null // Resets the UI back to "Play" when finished!
            }
            _playingAudioPath.value = filePath
        }
    }

    fun stopAudio() {
        com.oblutack.timenote.feature_timer.domain.AudioLocator.audioPlayer?.stop()
        _playingAudioPath.value = null
    }

    fun startRecordingForTimenote(timenoteId: String) {
        _recordingTimenoteId.value = timenoteId
        val fileName = "SessionMemo_$timenoteId"
        com.oblutack.timenote.feature_timer.domain.AudioLocator.audioRecorder?.startRecording(fileName)
    }

    fun stopRecordingForTimenote() {
        val timenoteId = _recordingTimenoteId.value ?: return
        val savedPath = com.oblutack.timenote.feature_timer.domain.AudioLocator.audioRecorder?.stopRecording()

        _recordingTimenoteId.value = null

        if (savedPath != null) {
            viewModelScope.launch {
                // Fetch the existing note
                val existingNote = com.oblutack.timenote.data.repository.SessionRepository.getTimenoteById(timenoteId)
                if (existingNote != null) {
                    // Append the new voice note to the list
                    val updatedList = existingNote.voiceNotes + savedPath
                    val updatedNote = existingNote.copy(voiceNotes = updatedList)
                    // Overwrite the DB with the updated note
                    com.oblutack.timenote.data.repository.SessionRepository.saveTimenote(updatedNote)
                }
            }
        }
    }

    fun deleteVoiceNote(timenoteId: String, pathToDelete: String) {
        viewModelScope.launch {
            val existingNote = com.oblutack.timenote.data.repository.SessionRepository.getTimenoteById(timenoteId)
            if (existingNote != null) {
                // Remove the target path from the list
                val updatedList = existingNote.voiceNotes - pathToDelete
                val updatedNote = existingNote.copy(voiceNotes = updatedList)
                com.oblutack.timenote.data.repository.SessionRepository.saveTimenote(updatedNote)
            }
        }
    }

    fun toggleFolderPin(id: String) {
        SessionRepository.toggleFolderPin(id)
    }

    fun toggleTimenotePin(id: String) {
        SessionRepository.toggleTimenotePin(id)
    }

    // --- DELETE CASCADER STATE ---
    private val _sessionPendingDelete = MutableStateFlow<com.oblutack.timenote.feature_history.domain.Timenote?>(null)
    val sessionPendingDelete = _sessionPendingDelete.asStateFlow()

    private val _descendantCount = MutableStateFlow(0)
    val descendantCount = _descendantCount.asStateFlow()

    fun requestDelete(session: com.oblutack.timenote.feature_history.domain.Timenote) {
        val descendants = com.oblutack.timenote.data.repository.SessionRepository.getDescendantIds(session.id)
        if (descendants.isNotEmpty()) {
            // It has children! Pause and ask the user.
            _descendantCount.value = descendants.size
            _sessionPendingDelete.value = session
        } else {
            // No children. Delete instantly.
            com.oblutack.timenote.data.repository.SessionRepository.deleteTimenote(session.id)
        }
    }

    fun confirmDelete(cascade: Boolean) {
        val session = _sessionPendingDelete.value ?: return
        if (cascade) {
            com.oblutack.timenote.data.repository.SessionRepository.cascadeSoftDeleteTimenote(session.id)
        } else {
            com.oblutack.timenote.data.repository.SessionRepository.deleteAndOrphanChildren(session.id)
        }
        cancelDelete()
    }

    fun cancelDelete() {
        _sessionPendingDelete.value = null
        _descendantCount.value = 0
    }

    // --- GRAPH SCREEN STATE ---
    private val _selectedGraphNodeId = MutableStateFlow<String?>(null)
    val selectedGraphNodeId = _selectedGraphNodeId.asStateFlow()

    fun selectGraphNode(id: String?) {
        _selectedGraphNodeId.value = id
    }

    // Mathematically calculates the total time of a node + ALL descendants
    fun calculateFamilyTime(nodeId: String): String {
        val allNotes = sessions.value
        val descendants = com.oblutack.timenote.data.repository.SessionRepository.getDescendantIds(nodeId)

        // Find the parent + all children
        val familyNodes = allNotes.filter { it.id == nodeId || descendants.contains(it.id) }

        val totalActiveSeconds = familyNodes.sumOf { it.activeSeconds }

        val hours = totalActiveSeconds / 3600
        val minutes = (totalActiveSeconds % 3600) / 60
        val seconds = totalActiveSeconds % 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}