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
import com.oblutack.timenote.core.SessionClock
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.getCurrentTimeMillis
import com.oblutack.timenote.feature_timer.domain.AudioRecorder
import com.oblutack.timenote.feature_timer.domain.TimerServiceCommand
import com.oblutack.timenote.feature_timer.domain.TimerServiceManager
import kotlinx.coroutines.flow.Flow

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

    val availableFolders: List<ProjectFolder> = emptyList(),
    val selectedFolder: ProjectFolder? = null,

    val isManageTagsSheetOpen: Boolean = false,
    val tagBeingEditedId: String? = null,

    val parentTimenoteId: String? = null,
    val parentWaypointId: String? = null,

    val parentSessionTitle: String? = null,
    val parentWaypointTitle: String? = null,

    val isRecordingVoiceMemo: Boolean = false,
    val voiceMemoDuration: String = "00:00",

    )

class TimerViewModel(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val timerServiceManager: TimerServiceManager,
    private val audioRecorder: AudioRecorder,
    private val serviceCommands: Flow<TimerServiceCommand>,
    private val now: () -> Long = ::getCurrentTimeMillis
) : ViewModel() {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var timerJob: Job? = null

    // --- ABSOLUTE TIME TRACKING ---
    private val clock = SessionClock()

    // Prevents restoring the backup multiple times in a row
    private var hasRestoredBackup = false

    private var frozenActiveSeconds = 0
    private var frozenPauseSeconds = 0

    // Folder/tag ids from a restored backup. The repository flows may not have emitted yet
    // when the backup is read, so these are resolved whenever the flows deliver data.
    private var pendingRestoreFolderId: String? = null
    private var pendingRestoreCategoryIds: List<String> = emptyList()

    // Skips redundant notification updates (the loop ticks 4x/sec, the text changes 1x/sec)
    private var lastNotificationKey: String? = null

    // NATIVE JSON PARSER: Ignores unknown data and prevents crashes!
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    init {
        // 1. Listen for past Timenotes (To show "Last Session")
        viewModelScope.launch {
            sessionRepository.timenotes.collect { notes ->
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
            sessionRepository.tags.collect { dbTags ->
                val restoredIds = pendingRestoreCategoryIds
                // ids stay pending until a tag with that id actually shows up
                pendingRestoreCategoryIds = restoredIds.filter { id -> dbTags.none { it.id == id } }
                _state.update { currentState ->
                    // Re-link selected categories to the fresh DB data (plus any ids awaiting restore)
                    val selectedIds = (currentState.selectedCategories.map { it.id } + restoredIds).distinct()
                    val updatedSelected = selectedIds.mapNotNull { id -> dbTags.find { it.id == id } }
                    currentState.copy(availableTags = dbTags, selectedCategories = updatedSelected)
                }
            }
        }

        // 3. Load Folders
        viewModelScope.launch {
            sessionRepository.folders.collect { dbFolders ->
                val restoredId = pendingRestoreFolderId
                // stays pending until a folder with that id actually shows up
                if (restoredId != null && dbFolders.any { it.id == restoredId }) pendingRestoreFolderId = null
                _state.update { currentState ->
                    val folderId = currentState.selectedFolder?.id ?: restoredId
                    val updatedSelectedFolder = dbFolders.find { it.id == folderId }
                    currentState.copy(availableFolders = dbFolders, selectedFolder = updatedSelectedFolder)
                }
            }
        }

        // 4. RESTORE BACKUP (The Swipe-To-Kill Savior)
        viewModelScope.launch {
            settingsRepository.activeSessionBackupFlow.collect { jsonString ->
                if (jsonString != null && !hasRestoredBackup) {
                    hasRestoredBackup = true
                    try {
                        val backup = jsonParser.decodeFromString<ActiveSessionBackup>(jsonString) // <-- USES NEW PARSER

                        clock.restore(
                            startMillis = backup.startTimeMillis,
                            totalPauseMillis = backup.totalPauseMillis,
                            pauseStartMillis = backup.lastPauseStartTimeMillis,
                            isPaused = backup.isPaused
                        )

                        // Resolve against what the repository already has; anything not loaded yet
                        // is picked up by the tag/folder collectors above.
                        val knownTags = sessionRepository.tags.value
                        val knownFolders = sessionRepository.folders.value
                        val restoredTags = backup.selectedCategoryIds.mapNotNull { id -> knownTags.find { it.id == id } }
                        val restoredFolder = knownFolders.find { it.id == backup.selectedFolderId }
                        pendingRestoreCategoryIds = backup.selectedCategoryIds.filter { id -> knownTags.none { it.id == id } }
                        pendingRestoreFolderId = backup.selectedFolderId?.takeIf { restoredFolder == null }

                        _state.update { it.copy(
                            isRunning = true,
                            isPaused = backup.isPaused,
                            sessionTitle = backup.sessionTitle,
                            timelineEvents = backup.timelineEvents,
                            selectedCategories = restoredTags,
                            selectedFolder = restoredFolder,
                            parentTimenoteId = backup.parentTimenoteId,
                            parentWaypointId = backup.parentWaypointId
                        )}

                        timerServiceManager.startService()
                        startTicking()
                    } catch (e: Exception) {
                        // If anything goes wrong, safely clear the corrupted data without crashing!
                        com.oblutack.timenote.core.logError("TimerViewModel", "Discarding unreadable session backup", e)
                        settingsRepository.saveActiveSession(null)
                    }
                }
            }
        }

        // 5. Listen for Android Notification Buttons
        viewModelScope.launch {
            serviceCommands.collect { command ->
                when (command) {
                    TimerServiceCommand.PAUSE -> onAction(TimerAction.Pause)
                    TimerServiceCommand.RESUME -> onAction(TimerAction.Resume)
                    TimerServiceCommand.END -> onAction(TimerAction.EndFromNotification)
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
                pendingRestoreCategoryIds = emptyList()
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
                    sessionRepository.saveTag(newTag)
                }
                _state.update { it.copy(isCreateTagDialogOpen = false, newTagName = "", newTagDescription = "", tagBeingEditedId = null) }
            }
            is TimerAction.ToggleTagMenu -> _state.update { it.copy(isTagMenuExpanded = !it.isTagMenuExpanded) }
            is TimerAction.ToggleTagsRowVisibility -> _state.update { it.copy(isTagsRowVisible = !it.isTagsRowVisible) }

            is TimerAction.SelectFolder -> {
                pendingRestoreFolderId = null
                val newSelection = if (_state.value.selectedFolder?.id == action.folder?.id) null else action.folder
                _state.update { it.copy(selectedFolder = newSelection) }
                if (_state.value.isRunning) backupCurrentState()
            }

            is TimerAction.OpenManageTagsSheet -> _state.update { it.copy(isManageTagsSheetOpen = true) }
            is TimerAction.CloseManageTagsSheet -> _state.update { it.copy(isManageTagsSheetOpen = false) }
            is TimerAction.DeleteTag -> sessionRepository.deleteTag(action.tagId)
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
                    audioRecorder.startRecording(fileName)
                }
            }
            is TimerAction.StopVoiceMemo -> {
                val savedPath = audioRecorder.stopRecording()
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
                        val parentSession = sessionRepository.getTimenoteById(action.parentId)
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

        clock.start(now())
        hasRestoredBackup = true

        addEventToTimeline("Session Started", EventType.START)

        if (_state.value.parentSessionTitle != null && _state.value.parentWaypointTitle != null) {
            val branchMessage = "Branched from: ${_state.value.parentWaypointTitle} (${_state.value.parentSessionTitle})"
            addEventToTimeline(branchMessage, EventType.NOTE, Color(0xFF9C27B0)) // Purple to signify a branch!
        }

        _state.update { it.copy(isRunning = true, isPaused = false) }

        timerServiceManager.startService()
        backupCurrentState()
        startTicking()
    }

    private fun pauseTimer() {
        if (!_state.value.isRunning || _state.value.isPaused) return

        clock.pause(now())
        addEventToTimeline("Paused", EventType.PAUSE)

        _state.update { it.copy(isPaused = true) }
        backupCurrentState()
    }

    private fun resumeTimer() {
        if (!_state.value.isPaused) return

        val pauseDurationMillis = clock.resume(now())

        val pauseDurationStr = formatTime((pauseDurationMillis / 1000).toInt())
        addEventToTimeline("Resumed (Break was $pauseDurationStr)", EventType.RESUME)

        _state.update { it.copy(isPaused = false) }
        backupCurrentState()
    }

    private fun endTimer(forceSave: Boolean = false) {
        if (!_state.value.isRunning && !_state.value.isPaused) return

        timerJob?.cancel()
        timerServiceManager.stopService()

        // --- THE FIX: Freeze the math right now, BEFORE we change the state! ---
        val now = now()
        frozenActiveSeconds = clock.activeSeconds(now)
        frozenPauseSeconds = clock.pauseSeconds(now)
        // -----------------------------------------------------------------------

        // 1. SET TO FALSE IMMEDIATELY so backups are blocked!
        _state.update { it.copy(isRunning = false, isPaused = false) }

        // 2. Add the timeline event (Now it won't back up)
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        addEventToTimeline("Session Ended: $title", EventType.END)

        // 3. Nuke the backup permanently
        viewModelScope.launch { settingsRepository.saveActiveSession(null) }

        if (forceSave || _state.value.selectedCategories.isNotEmpty()) {
            executeSave(_state.value.selectedCategories)
        } else {
            _state.update { it.copy(isCategoryPopupOpen = true) }
        }
    }

    private fun executeSave(categories: List<TimenoteFolder>) {
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }

        val timestampId = platformSpecificId()

        val newTimenote = Timenote(
            id = timestampId,
            folderId = _state.value.selectedFolder?.id,
            title = title,
            description = "",
            // --- THE FIX: Just use the math we already froze! ---
            duration = formatTime(frozenActiveSeconds + frozenPauseSeconds),
            activeSeconds = frozenActiveSeconds,
            pauseSeconds = frozenPauseSeconds,
            createdAt = now(),
            // ----------------------------------------------------
            tags = categories,
            timelineEvents = _state.value.timelineEvents,
            parentTimenoteId = _state.value.parentTimenoteId,
            parentWaypointId = _state.value.parentWaypointId
        )

        sessionRepository.saveTimenote(newTimenote)

        _state.update { it.copy(
            isCategoryPopupOpen = false,
            selectedCategories = categories,
            lastSessionTitle = title,
            sessionTitle = "",
            // The branch is finished; clear the display titles too, or the banner stays up and the
            // next unrelated session would get a bogus "Branched from" waypoint.
            parentTimenoteId = null,
            parentWaypointId = null,
            parentSessionTitle = null,
            parentWaypointTitle = null
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
        lastNotificationKey = null
        timerJob = viewModelScope.launch {
            while (true) {
                delay(250L)

                val now = now()

                if (_state.value.isPaused) {
                    // 1. The Pause Timer (Ticking)
                    val formattedPause = formatTime((clock.currentPauseMillis(now) / 1000).toInt())

                    // 2. The Main Timer (FROZEN)
                    // We freeze it at the exact moment you hit the Pause button
                    val formattedFrozenTotal = formatTime((clock.displayElapsedMillis(now) / 1000).toInt())

                    _state.update { it.copy(
                        currentPauseTime = formattedPause,
                        displayTime = formattedFrozenTotal // <-- Now it stops moving!
                    ) }

                    val pausedKey = "paused|$formattedPause"
                    if (pausedKey != lastNotificationKey) {
                        lastNotificationKey = pausedKey
                        timerServiceManager.updateNotification(
                            title = "Paused", timeText = formattedPause, baseMillis = 0L, isPaused = true
                        )
                    }
                } else {
                    // 1. The Main Timer (Ticking Total Time)
                    val totalElapsedMillis = clock.displayElapsedMillis(now)
                    val formattedTotal = formatTime((totalElapsedMillis / 1000).toInt())

                    _state.update { it.copy(displayTime = formattedTotal) }

                    // NATIVE MATH: Tell Android OS to count the Total Time too!
                    val baseTimeForOS = now() - totalElapsedMillis
                    val notificationTitle = _state.value.sessionTitle.ifBlank { "Timenote Active" }

                    val runningKey = "running|$notificationTitle|$formattedTotal"
                    if (runningKey != lastNotificationKey) {
                        lastNotificationKey = runningKey
                        timerServiceManager.updateNotification(
                            title = notificationTitle,
                            timeText = formattedTotal,
                            baseMillis = baseTimeForOS,
                            isPaused = false
                        )
                    }
                }
            }
        }
    }

    private fun addEventToTimeline(title: String, type: EventType, color: Color? = null, audioPath: String? = null) {
        val now = now()
        val totalElapsedSeconds = clock.elapsedSeconds(now)

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
            startTimeMillis = clock.startMillis,
            totalPauseMillis = clock.totalPauseMillis,
            lastPauseStartTimeMillis = if (clock.isPaused) clock.pauseStartMillis else null,
            isPaused = _state.value.isPaused,
            timelineEvents = _state.value.timelineEvents,
            selectedFolderId = _state.value.selectedFolder?.id,
            selectedCategoryIds = _state.value.selectedCategories.map { it.id },
            parentTimenoteId = _state.value.parentTimenoteId,
            parentWaypointId = _state.value.parentWaypointId
        )
        val json = jsonParser.encodeToString(backup) // <-- USES NEW PARSER
        viewModelScope.launch { settingsRepository.saveActiveSession(json) }
    }

    private fun formatTime(totalSeconds: Int): String = com.oblutack.timenote.core.formatDuration(totalSeconds)

    companion object {
        private var idCounter = 0
    }

    private fun platformSpecificId(): String {
        idCounter++
        return "${now()}_$idCounter"
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
    data class SelectFolder(val folder: ProjectFolder?) : TimerAction()
    data object OpenManageTagsSheet : TimerAction()
    data object CloseManageTagsSheet : TimerAction()
    data class DeleteTag(val tagId: String) : TimerAction()
    data class EditTag(val tag: TimenoteFolder) : TimerAction()
    data class SetParentLinks(val parentId: String?, val waypointId: String?) : TimerAction()
    data object StartVoiceMemo : TimerAction()
    data object StopVoiceMemo : TimerAction()
}