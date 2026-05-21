package com.oblutack.timenote.feature_timer.presentation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.feature_timer.domain.ActiveSessionBackup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class TimerState(
    val displayTime: String = "00:00:00",
    val currentPauseTime: String = "00:00:00",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val sessionTitle: String = "",
    val lastSessionTitle: String = "",
    val timelineEvents: List<TimelineEvent> = emptyList(),

    val isAddNoteDialogOpen: Boolean = false,
    val dialogNoteText: String = "",
    val dialogNoteColor: Color = Color(0xFF4FA8F9),

    val selectedCategories: List<TimenoteFolder> = emptyList(),
    val isCategoryPopupOpen: Boolean = false,
    val availableTags: List<TimenoteFolder> = emptyList(),

    val isCreateTagDialogOpen: Boolean = false,
    val newTagName: String = "",
    val newTagDescription: String = "",
    val newTagColor: Color = Color(0xFF4FA8F9),
    val isTagMenuExpanded: Boolean = false,
    val isTagsRowVisible: Boolean = false,

    val availableFolders: List<com.oblutack.timenote.feature_history.domain.ProjectFolder> = emptyList(),
    val selectedFolder: com.oblutack.timenote.feature_history.domain.ProjectFolder? = null,

    val isManageTagsSheetOpen: Boolean = false,
    val tagBeingEditedId: String? = null,

    val parentTimenoteId: String? = null,
    val parentWaypointId: String? = null,

    val parentSessionTitle: String? = null,
    val parentWaypointTitle: String? = null,

    val isRecordingVoiceMemo: Boolean = false,
    val voiceMemoDuration: String = "00:00",

    )

class TimerViewModel : ViewModel() {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var timerJob: Job? = null

    // --- ABSOLUTE TIME TRACKING ---
    private var startTimeMillis = 0L
    private var totalPauseMillis = 0L
    private var currentPauseStartMillis = 0L

    // Prevents restoring the backup multiple times in a row
    private var hasRestoredBackup = false

    private var frozenActiveSeconds = 0
    private var frozenPauseSeconds = 0

    // NATIVE JSON PARSER: Ignores unknown data and prevents crashes!
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    init {
        // 1. Listen for past Timenotes (To show "Last Session")
        viewModelScope.launch {
            com.oblutack.timenote.data.repository.SessionRepository.timenotes.collect { notes ->
                if (!_state.value.isRunning && !_state.value.isPaused && _state.value.timelineEvents.isEmpty()) {
                    notes.firstOrNull()?.let { lastNote ->
                        _state.update { it.copy(
                            displayTime = lastNote.duration,
                            lastSessionTitle = lastNote.title,
                            sessionTitle = "",
                            timelineEvents = lastNote.timelineEvents
                        )}
                    }
                }
            }
        }

        // 2. Load Tags
        viewModelScope.launch {
            com.oblutack.timenote.data.repository.SessionRepository.tags.collect { dbTags ->
                _state.update { currentState ->
                    // Re-link selected categories to the fresh DB data
                    val updatedSelected = currentState.selectedCategories.mapNotNull { selected ->
                        dbTags.find { it.id == selected.id }
                    }
                    currentState.copy(availableTags = dbTags, selectedCategories = updatedSelected)
                }
            }
        }

        // 3. Load Folders
        viewModelScope.launch {
            com.oblutack.timenote.data.repository.SessionRepository.folders.collect { dbFolders ->
                _state.update { currentState ->
                    val updatedSelectedFolder = dbFolders.find { it.id == currentState.selectedFolder?.id }
                    currentState.copy(availableFolders = dbFolders, selectedFolder = updatedSelectedFolder)
                }
            }
        }

        // 4. RESTORE BACKUP (The Swipe-To-Kill Savior)
        viewModelScope.launch {
            com.oblutack.timenote.data.repository.SettingsRepository.activeSessionBackupFlow.collect { jsonString ->
                if (jsonString != null && !hasRestoredBackup) {
                    hasRestoredBackup = true
                    try {
                        val backup = jsonParser.decodeFromString<ActiveSessionBackup>(jsonString) // <-- USES NEW PARSER

                        startTimeMillis = backup.startTimeMillis
                        totalPauseMillis = backup.totalPauseMillis
                        currentPauseStartMillis = backup.lastPauseStartTimeMillis ?: 0L

                        _state.update { it.copy(
                            isRunning = true,
                            isPaused = backup.isPaused,
                            sessionTitle = backup.sessionTitle,
                            timelineEvents = backup.timelineEvents,
                            parentTimenoteId = backup.parentTimenoteId,
                            parentWaypointId = backup.parentWaypointId
                        )}

                        com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.startService()
                        startTicking()
                    } catch (e: Exception) {
                        // If anything goes wrong, safely clear the corrupted data without crashing!
                        e.printStackTrace()
                        com.oblutack.timenote.data.repository.SettingsRepository.saveActiveSession(null)
                    }
                }
            }
        }

        // 5. Listen for Android Notification Buttons
        viewModelScope.launch {
            com.oblutack.timenote.feature_timer.domain.ServiceLocator.serviceCommands.collect { command ->
                when (command) {
                    "PAUSE" -> onAction(TimerAction.Pause)
                    "RESUME" -> onAction(TimerAction.Resume)
                    "END_FROM_NOTIFICATION" -> onAction(TimerAction.EndFromNotification) // <-- CHANGED
                }
            }
        }
    }

    fun onAction(action: TimerAction) {
        when (action) {
            is TimerAction.Start -> startTimer()
            is TimerAction.Pause -> pauseTimer()
            is TimerAction.Resume -> resumeTimer()
            is TimerAction.End -> endTimer(forceSave = false)
            is TimerAction.EndFromNotification -> endTimer(forceSave = true) // <-- ADDED
            is TimerAction.UpdateSessionTitle -> {
                _state.update { it.copy(sessionTitle = action.text) }
                if (_state.value.isRunning) backupCurrentState()
            }

            is TimerAction.OpenAddNoteDialog -> {
                if (_state.value.isRunning) _state.update { it.copy(isAddNoteDialogOpen = true) }
            }
            is TimerAction.CloseAddNoteDialog -> _state.update { it.copy(isAddNoteDialogOpen = false, dialogNoteText = "") }
            is TimerAction.UpdateDialogNoteText -> _state.update { it.copy(dialogNoteText = action.text) }
            is TimerAction.UpdateDialogNoteColor -> _state.update { it.copy(dialogNoteColor = action.color) }
            is TimerAction.SaveNote -> saveNote()

            is TimerAction.ToggleCategory -> {
                _state.update { currentState ->
                    val currentList = currentState.selectedCategories
                    val newList = if (currentList.any { it.id == action.category.id }) {
                        currentList.filter { it.id != action.category.id }
                    } else {
                        currentList + action.category
                    }
                    currentState.copy(selectedCategories = newList)
                }
                if (_state.value.isRunning) backupCurrentState()
            }
            is TimerAction.SkipCategoriesAndSave -> executeSave(emptyList())
            is TimerAction.ConfirmCategoriesAndSave -> executeSave(_state.value.selectedCategories)

            is TimerAction.OpenCreateTagDialog -> _state.update { it.copy(isCreateTagDialogOpen = true) }
            is TimerAction.CloseCreateTagDialog -> _state.update { it.copy(isCreateTagDialogOpen = false, newTagName = "", newTagDescription = "") }
            is TimerAction.UpdateNewTagName -> _state.update { it.copy(newTagName = action.name) }
            is TimerAction.UpdateNewTagDescription -> _state.update { it.copy(newTagDescription = action.description) }
            is TimerAction.UpdateNewTagColor -> _state.update { it.copy(newTagColor = action.color) }
            is TimerAction.SaveNewTag -> {
                val name = _state.value.newTagName
                if (name.isNotBlank()) {
                    val tagIdToSave = _state.value.tagBeingEditedId ?: platformSpecificId()
                    val newTag = TimenoteFolder(
                        id = tagIdToSave,
                        name = name,
                        description = _state.value.newTagDescription,
                        sessionCount = 0,
                        color = _state.value.newTagColor
                    )
                    com.oblutack.timenote.data.repository.SessionRepository.saveTag(newTag)
                }
                _state.update { it.copy(isCreateTagDialogOpen = false, newTagName = "", newTagDescription = "", tagBeingEditedId = null) }
            }
            is TimerAction.ToggleTagMenu -> _state.update { it.copy(isTagMenuExpanded = !it.isTagMenuExpanded) }
            is TimerAction.ToggleTagsRowVisibility -> _state.update { it.copy(isTagsRowVisible = !it.isTagsRowVisible) }

            is TimerAction.SelectFolder -> {
                val newSelection = if (_state.value.selectedFolder?.id == action.folder?.id) null else action.folder
                _state.update { it.copy(selectedFolder = newSelection) }
                if (_state.value.isRunning) backupCurrentState()
            }

            is TimerAction.OpenManageTagsSheet -> _state.update { it.copy(isManageTagsSheetOpen = true) }
            is TimerAction.CloseManageTagsSheet -> _state.update { it.copy(isManageTagsSheetOpen = false) }
            is TimerAction.DeleteTag -> com.oblutack.timenote.data.repository.SessionRepository.deleteTag(action.tagId)
            is TimerAction.EditTag -> {
                _state.update { it.copy(
                    isManageTagsSheetOpen = false,
                    isCreateTagDialogOpen = true,
                    tagBeingEditedId = action.tag.id,
                    newTagName = action.tag.name,
                    newTagDescription = action.tag.description ?: "",
                    newTagColor = action.tag.color
                ) }
            }
            is TimerAction.StartVoiceMemo -> {
                if (_state.value.isRunning) {
                    _state.update { it.copy(isRecordingVoiceMemo = true, voiceMemoDuration = "00:00") }
                    val fileName = "VoiceMemo_${platformSpecificId()}"
                    com.oblutack.timenote.feature_timer.domain.AudioLocator.audioRecorder?.startRecording(fileName)
                }
            }
            is TimerAction.StopVoiceMemo -> {
                val savedPath = com.oblutack.timenote.feature_timer.domain.AudioLocator.audioRecorder?.stopRecording()
                _state.update { it.copy(isRecordingVoiceMemo = false) }

                if (savedPath != null && _state.value.isRunning) {
                    addEventToTimeline("Voice Memo attached", EventType.NOTE, null, savedPath)
                }
            }
            is TimerAction.SetParentLinks -> {
                _state.update { it.copy(parentTimenoteId = action.parentId, parentWaypointId = action.waypointId) }

                // Fetch the parent data so the UI can display it!
                if (action.parentId != null) {
                    viewModelScope.launch {
                        val parentSession = com.oblutack.timenote.data.repository.SessionRepository.getTimenoteById(action.parentId)
                        if (parentSession != null) {
                            val waypoint = parentSession.timelineEvents.find { it.id == action.waypointId }
                            _state.update { currentState ->
                                currentState.copy(
                                    parentSessionTitle = parentSession.title,
                                    parentWaypointTitle = waypoint?.title
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startTimer() {
        if (_state.value.isRunning) return

        if (_state.value.timelineEvents.isNotEmpty()) {
            _state.update {
                TimerState(
                    sessionTitle = it.sessionTitle,
                    selectedCategories = it.selectedCategories,
                    availableTags = it.availableTags,
                    availableFolders = it.availableFolders,
                    selectedFolder = it.selectedFolder,
                    // --- THE FIX: DON'T FORGET THE PARENT! ---
                    parentTimenoteId = it.parentTimenoteId,
                    parentWaypointId = it.parentWaypointId,
                    parentSessionTitle = it.parentSessionTitle,
                    parentWaypointTitle = it.parentWaypointTitle
                    // -----------------------------------------
                )
            }
        }

        startTimeMillis = com.oblutack.timenote.getCurrentTimeMillis()
        totalPauseMillis = 0L
        currentPauseStartMillis = 0L
        hasRestoredBackup = true

        addEventToTimeline("Session Started", EventType.START)

        if (_state.value.parentSessionTitle != null && _state.value.parentWaypointTitle != null) {
            val branchMessage = "Branched from: ${_state.value.parentWaypointTitle} (${_state.value.parentSessionTitle})"
            addEventToTimeline(branchMessage, EventType.NOTE, Color(0xFF9C27B0)) // Purple to signify a branch!
        }

        _state.update { it.copy(isRunning = true, isPaused = false) }

        com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.startService()
        backupCurrentState()
        startTicking()
    }

    private fun pauseTimer() {
        if (!_state.value.isRunning || _state.value.isPaused) return

        currentPauseStartMillis = com.oblutack.timenote.getCurrentTimeMillis()
        addEventToTimeline("Paused", EventType.PAUSE)

        _state.update { it.copy(isPaused = true) }
        backupCurrentState()
    }

    private fun resumeTimer() {
        if (!_state.value.isPaused) return

        val pauseDurationMillis = com.oblutack.timenote.getCurrentTimeMillis() - currentPauseStartMillis
        totalPauseMillis += pauseDurationMillis
        currentPauseStartMillis = 0L

        val pauseDurationStr = formatTime((pauseDurationMillis / 1000).toInt())
        addEventToTimeline("Resumed (Break was $pauseDurationStr)", EventType.RESUME)

        _state.update { it.copy(isPaused = false) }
        backupCurrentState()
    }

    private fun endTimer(forceSave: Boolean = false) {
        if (!_state.value.isRunning && !_state.value.isPaused) return

        timerJob?.cancel()
        com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.stopService()

        // --- THE FIX: Freeze the math right now, BEFORE we change the state! ---
        val now = com.oblutack.timenote.getCurrentTimeMillis()
        val finalActiveMillis = now - startTimeMillis - totalPauseMillis - (if (_state.value.isPaused) now - currentPauseStartMillis else 0L)
        frozenActiveSeconds = (finalActiveMillis / 1000).toInt()
        frozenPauseSeconds = (totalPauseMillis / 1000).toInt() + (if (_state.value.isPaused) ((now - currentPauseStartMillis) / 1000).toInt() else 0)
        // -----------------------------------------------------------------------

        // 1. SET TO FALSE IMMEDIATELY so backups are blocked!
        _state.update { it.copy(isRunning = false, isPaused = false) }

        // 2. Add the timeline event (Now it won't back up)
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        addEventToTimeline("Session Ended: $title", EventType.END)

        // 3. Nuke the backup permanently
        viewModelScope.launch { com.oblutack.timenote.data.repository.SettingsRepository.saveActiveSession(null) }

        if (forceSave || _state.value.selectedCategories.isNotEmpty()) {
            executeSave(_state.value.selectedCategories)
        } else {
            _state.update { it.copy(isCategoryPopupOpen = true) }
        }
    }

    private fun executeSave(categories: List<TimenoteFolder>) {
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }

        val timestampId = platformSpecificId()

        val newTimenote = com.oblutack.timenote.feature_history.domain.Timenote(
            id = timestampId,
            folderId = _state.value.selectedFolder?.id,
            title = title,
            description = "",
            // --- THE FIX: Just use the math we already froze! ---
            duration = formatTime(frozenActiveSeconds + frozenPauseSeconds),
            activeSeconds = frozenActiveSeconds,
            pauseSeconds = frozenPauseSeconds,
            createdAt = com.oblutack.timenote.getCurrentTimeMillis(),
            // ----------------------------------------------------
            tags = categories,
            timelineEvents = _state.value.timelineEvents,
            parentTimenoteId = _state.value.parentTimenoteId,
            parentWaypointId = _state.value.parentWaypointId
        )

        com.oblutack.timenote.data.repository.SessionRepository.saveTimenote(newTimenote)

        _state.update { it.copy(
            isCategoryPopupOpen = false,
            selectedCategories = categories,
            lastSessionTitle = title,
            sessionTitle = "",
            parentTimenoteId = null,
            parentWaypointId = null
        ) }
    }

    private fun saveNote() {
        val noteText = _state.value.dialogNoteText
        if (noteText.isBlank() || !_state.value.isRunning) return

        addEventToTimeline("Note: $noteText", EventType.NOTE, _state.value.dialogNoteColor)

        _state.update { it.copy(
            isAddNoteDialogOpen = false,
            dialogNoteText = "",
            dialogNoteColor = Color(0xFF4FA8F9)
        ) }
        backupCurrentState()
    }

    private fun startTicking() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(250L)

                val now = com.oblutack.timenote.getCurrentTimeMillis()

                if (_state.value.isPaused) {
                    // 1. The Pause Timer (Ticking)
                    val currentPauseMillis = now - currentPauseStartMillis
                    val formattedPause = formatTime((currentPauseMillis / 1000).toInt())

                    // 2. The Main Timer (FROZEN)
                    // We freeze it at the exact moment you hit the Pause button
                    val frozenTotalMillis = currentPauseStartMillis - startTimeMillis
                    val formattedFrozenTotal = formatTime((frozenTotalMillis / 1000).toInt())

                    _state.update { it.copy(
                        currentPauseTime = formattedPause,
                        displayTime = formattedFrozenTotal // <-- Now it stops moving!
                    ) }

                    com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.updateNotification(
                        title = "Paused", timeText = formattedPause, baseMillis = 0L, isPaused = true
                    )
                } else {
                    // 1. The Main Timer (Ticking Total Time)
                    val totalElapsedMillis = now - startTimeMillis
                    val formattedTotal = formatTime((totalElapsedMillis / 1000).toInt())

                    _state.update { it.copy(displayTime = formattedTotal) }

                    // NATIVE MATH: Tell Android OS to count the Total Time too!
                    val baseTimeForOS = com.oblutack.timenote.getCurrentTimeMillis() - totalElapsedMillis

                    com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.updateNotification(
                        title = _state.value.sessionTitle.ifBlank { "Timenote Active" },
                        timeText = formattedTotal,
                        baseMillis = baseTimeForOS,
                        isPaused = false
                    )
                }
            }
        }
    }

    private fun addEventToTimeline(title: String, type: EventType, color: Color? = null, audioPath: String? = null) {
        val now = com.oblutack.timenote.getCurrentTimeMillis()
        val activeMillis = now - startTimeMillis - totalPauseMillis - (if (_state.value.isPaused) now - currentPauseStartMillis else 0L)
        val totalElapsedSeconds = (activeMillis / 1000).toInt() + (totalPauseMillis / 1000).toInt() + (if (_state.value.isPaused) ((now - currentPauseStartMillis) / 1000).toInt() else 0)

        val newEvent = TimelineEvent(
            id = platformSpecificId(),
            title = title,
            timestamp = formatTime(totalElapsedSeconds),
            type = type,
            isLastItem = false,
            color = color,
            audioPath = audioPath // <-- PASS THE PATH HERE
        )

        _state.update { currentState ->
            val updatedList = listOf(newEvent) + currentState.timelineEvents
            val finalizedList = updatedList.mapIndexed { index, event ->
                event.copy(isLastItem = index == updatedList.lastIndex)
            }
            currentState.copy(timelineEvents = finalizedList)
        }

        backupCurrentState()
    }

    private fun backupCurrentState() {

        if (!_state.value.isRunning && !_state.value.isPaused) return

        val backup = ActiveSessionBackup(
            sessionTitle = _state.value.sessionTitle,
            startTimeMillis = startTimeMillis,
            totalPauseMillis = totalPauseMillis,
            lastPauseStartTimeMillis = if (_state.value.isPaused) currentPauseStartMillis else null,
            isPaused = _state.value.isPaused,
            timelineEvents = _state.value.timelineEvents,
            selectedFolderId = _state.value.selectedFolder?.id,
            selectedCategoryIds = _state.value.selectedCategories.map { it.id },
            parentTimenoteId = _state.value.parentTimenoteId,
            parentWaypointId = _state.value.parentWaypointId
        )
        val json = jsonParser.encodeToString(backup) // <-- USES NEW PARSER
        viewModelScope.launch { com.oblutack.timenote.data.repository.SettingsRepository.saveActiveSession(json) }
    }

    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    companion object {
        private var idCounter = 0
    }

    private fun platformSpecificId(): String {
        idCounter++
        return "${com.oblutack.timenote.getCurrentTimeMillis()}_$idCounter"
    }
}

sealed class TimerAction {
    data object Start : TimerAction()
    data object Pause : TimerAction()
    data object Resume : TimerAction()
    data object End : TimerAction()
    data object EndFromNotification : TimerAction()
    data class UpdateSessionTitle(val text: String) : TimerAction()
    data object OpenAddNoteDialog : TimerAction()
    data object CloseAddNoteDialog : TimerAction()
    data class UpdateDialogNoteText(val text: String) : TimerAction()
    data class UpdateDialogNoteColor(val color: Color) : TimerAction()
    data object SaveNote : TimerAction()
    data class ToggleCategory(val category: TimenoteFolder) : TimerAction()
    data object SkipCategoriesAndSave : TimerAction()
    data object ConfirmCategoriesAndSave : TimerAction()
    data object OpenCreateTagDialog : TimerAction()
    data object CloseCreateTagDialog : TimerAction()
    data class UpdateNewTagName(val name: String) : TimerAction()
    data class UpdateNewTagDescription(val description: String) : TimerAction()
    data class UpdateNewTagColor(val color: Color) : TimerAction()
    data object SaveNewTag : TimerAction()
    data object ToggleTagMenu : TimerAction()
    data object ToggleTagsRowVisibility : TimerAction()
    data class SelectFolder(val folder: com.oblutack.timenote.feature_history.domain.ProjectFolder?) : TimerAction()
    data object OpenManageTagsSheet : TimerAction()
    data object CloseManageTagsSheet : TimerAction()
    data class DeleteTag(val tagId: String) : TimerAction()
    data class EditTag(val tag: TimenoteFolder) : TimerAction()
    data class SetParentLinks(val parentId: String?, val waypointId: String?) : TimerAction()
    data object StartVoiceMemo : TimerAction()
    data object StopVoiceMemo : TimerAction()
}