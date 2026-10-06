package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.toLocalDateTime
import com.oblutack.timenote.feature_history.domain.DailySummary
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.getCurrentTimeMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import com.oblutack.timenote.feature_timer.domain.AudioPlayer
import com.oblutack.timenote.feature_timer.domain.AudioRecorder
import com.oblutack.timenote.core.newId
import com.oblutack.timenote.core.AudioFiles

enum class SortOption(val displayName: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    LONGEST("Longest Duration"),
    SHORTEST("Shortest Duration")
}

class HistoryViewModel(
    private val sessionRepository: SessionRepository,
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    private val audioFiles: AudioFiles,
    private val now: () -> Long = ::getCurrentTimeMillis
) : ViewModel() {
    val sessions = sessionRepository.timenotes

    val heatmapData: StateFlow<Map<String, Int>> = sessions.map { allSessions ->
        val map = mutableMapOf<String, Int>()
        allSessions.forEach { session ->
            // Safely convert the timestamp to a local date string
            val instant = Instant.fromEpochMilliseconds(
                if (session.createdAt > 0L) session.createdAt else now()
            )
            val dateStr = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

            // Add this session's active time to that day's total
            map[dateStr] = (map[dateStr] ?: 0) + session.activeSeconds
        }
        map
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // --- STREAK TRACKING ---
    val streaks: StateFlow<Pair<Int, Int>> = heatmapData.map { data ->
        val activeDates = data.filter { it.value > 0 }.keys
            .map { LocalDate.parse(it) }
            .toSet()
        val today = Instant.fromEpochMilliseconds(now())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        com.oblutack.timenote.core.calculateStreaks(activeDates, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0))

    // --- DAILY SUMMARY STATE ---
    private val _selectedDailySummary = MutableStateFlow<DailySummary?>(null)
    val selectedDailySummary = _selectedDailySummary.asStateFlow()

    fun selectDateForSummary(date: LocalDate) {
        val sessionsOnDate = sessions.value.filter { session ->
            val instant = Instant.fromEpochMilliseconds(
                if (session.createdAt > 0L) session.createdAt else now()
            )
            instant.toLocalDateTime(TimeZone.currentSystemDefault()).date == date
        }

        if (sessionsOnDate.isEmpty()) {
            _selectedDailySummary.value = DailySummary(date, 0, 0, null)
            return
        }

        val totalSecs = sessionsOnDate.sumOf { it.activeSeconds }
        val count = sessionsOnDate.size

        // Find the most used tag on that day!
        val topTag = sessionsOnDate.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key

        _selectedDailySummary.value = DailySummary(date, totalSecs, count, topTag)
    }

    fun closeDailySummary() {
        _selectedDailySummary.value = null
    }

    val folders = sessionRepository.folders
    val tags = sessionRepository.tags

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
        sessionRepository.deleteTimenote(id)
    }

    fun saveFolder(id: String? = null, name: String, description: String? = null, color: Color) { // <-- NEW PARAM
        val currentTime = now()
        val folderToSave = if (id == null) {
            ProjectFolder(
                id = newId(),
                name = name,
                description = description, // <-- PASSED IN
                color = color,
                createdAt = currentTime
            )
        } else {
            // Start from the stored folder so fields the editor does not show (pinned, ...) are kept
            val existing = folders.value.find { it.id == id }
            existing?.copy(name = name, description = description, color = color)
                ?: ProjectFolder(id = id, name = name, description = description, color = color, createdAt = currentTime)
        }
        sessionRepository.saveFolder(folderToSave)
    }

    fun deleteFolder(id: String) {
        // We need to implement sessionRepository.deleteFolder first but let's assume it exists or will be added
        sessionRepository.deleteFolder(id)
    }

    // --- AUDIO PLAYER STATE ---
    private val _playingAudioPath = MutableStateFlow<String?>(null)
    val playingAudioPath = _playingAudioPath.asStateFlow()
    private val _recordingTimenoteId = MutableStateFlow<String?>(null)
    val recordingTimenoteId = _recordingTimenoteId.asStateFlow()
    /** [audioRef] is what the database stores (a file name, or a legacy absolute path). */
    fun playAudio(audioRef: String) {
        val player = audioPlayer

        if (_playingAudioPath.value == audioRef && player.isPlaying()) {
            player.pause()
            _playingAudioPath.value = null
        } else {
            player.play(audioFiles.resolve(audioRef)) {
                _playingAudioPath.value = null // Resets the UI back to "Play" when finished!
            }
            _playingAudioPath.value = audioRef
        }
    }

    fun stopAudio() {
        audioPlayer.stop()
        _playingAudioPath.value = null
    }

    // True after a recording could not start (e.g. no microphone permission); cleared on the next attempt
    private val _micUnavailable = MutableStateFlow(false)
    val micUnavailable = _micUnavailable.asStateFlow()

    fun startRecordingForTimenote(timenoteId: String) {
        val started = audioRecorder.startRecording("SessionMemo_$timenoteId")
        _micUnavailable.value = !started
        if (started) _recordingTimenoteId.value = timenoteId
    }

    fun stopRecordingForTimenote() {
        val timenoteId = _recordingTimenoteId.value ?: return
        val savedPath = audioRecorder.stopRecording()

        _recordingTimenoteId.value = null

        if (savedPath != null) {
            sessionRepository.addVoiceNote(timenoteId, audioFiles.toRef(savedPath))
        }
    }

    fun deleteVoiceNote(timenoteId: String, pathToDelete: String) {
        sessionRepository.removeVoiceNote(timenoteId, pathToDelete)
    }

    fun toggleFolderPin(id: String) {
        sessionRepository.toggleFolderPin(id)
    }

    fun toggleTimenotePin(id: String) {
        sessionRepository.toggleTimenotePin(id)
    }

    // --- DELETE CASCADER STATE ---
    private val _sessionPendingDelete = MutableStateFlow<Timenote?>(null)
    val sessionPendingDelete = _sessionPendingDelete.asStateFlow()

    private val _descendantCount = MutableStateFlow(0)
    val descendantCount = _descendantCount.asStateFlow()

    fun requestDelete(session: Timenote) {
        val descendants = sessionRepository.getDescendantIds(session.id)
        if (descendants.isNotEmpty()) {
            // It has children! Pause and ask the user.
            _descendantCount.value = descendants.size
            _sessionPendingDelete.value = session
        } else {
            // No children. Delete instantly.
            sessionRepository.deleteTimenote(session.id)
        }
    }

    fun confirmDelete(cascade: Boolean) {
        val session = _sessionPendingDelete.value ?: return
        if (cascade) {
            sessionRepository.cascadeSoftDeleteTimenote(session.id)
        } else {
            sessionRepository.deleteAndOrphanChildren(session.id)
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
        val descendants = sessionRepository.getDescendantIds(nodeId)

        // Find the parent + all children
        val familyNodes = allNotes.filter { it.id == nodeId || descendants.contains(it.id) }

        return com.oblutack.timenote.core.formatDuration(familyNodes.sumOf { it.activeSeconds })
    }
}