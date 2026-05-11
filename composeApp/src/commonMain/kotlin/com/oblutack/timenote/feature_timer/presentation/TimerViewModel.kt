package com.oblutack.timenote.feature_timer.presentation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.oblutack.timenote.feature_history.domain.TimenoteFolder

data class TimerState(
    val displayTime: String = "00:00:00",
    val currentPauseTime: String = "00:00:00",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val sessionTitle: String = "",
    val lastSessionTitle: String = "",
    val timelineEvents: List<TimelineEvent> = emptyList(),

    // Dialog State
    val isAddNoteDialogOpen: Boolean = false,
    val dialogNoteText: String = "",
    val dialogNoteColor: Color = Color(0xFF4FA8F9),

    // Multi-select Category State
    val selectedCategories: List<TimenoteFolder> = emptyList(),
    val isCategoryPopupOpen: Boolean = false,

    val availableTags: List<TimenoteFolder> = emptyList(),

    val isCreateTagDialogOpen: Boolean = false,
    val newTagName: String = "",
    val newTagColor: Color = Color(0xFF4FA8F9),
    val newTagDescription: String = "",

    val isTagMenuExpanded: Boolean = false,
    val isTagsRowVisible: Boolean = false,

    val availableFolders: List<com.oblutack.timenote.feature_history.domain.ProjectFolder> = emptyList(), // NEW
    val selectedFolder: com.oblutack.timenote.feature_history.domain.ProjectFolder? = null,

    val isManageTagsSheetOpen: Boolean = false,
    val tagBeingEditedId: String? = null,

)

class TimerViewModel : ViewModel() {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var timerJob: Job? = null

    // Internal counters
    private var activeSeconds = 0
    private var currentPauseSeconds = 0
    private var totalPauseSeconds = 0

    init {
        // 1. Listen for past Timenotes
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

        // --- THE MISSING LINK: Listen for the Tags! ---
        viewModelScope.launch {
            com.oblutack.timenote.data.repository.SessionRepository.tags.collect { dbTags ->
                _state.update { it.copy(availableTags = dbTags) }
            }
        }

        // NEW: Listen for Folders!
        viewModelScope.launch {
            com.oblutack.timenote.data.repository.SessionRepository.folders.collect { dbFolders ->
                _state.update { it.copy(availableFolders = dbFolders) }
            }
        }
    }

    fun onAction(action: TimerAction) {
        when (action) {
            is TimerAction.Start -> startTimer()
            is TimerAction.Pause -> pauseTimer()
            is TimerAction.Resume -> resumeTimer()
            is TimerAction.End -> endTimer()
            is TimerAction.UpdateSessionTitle -> _state.update { it.copy(sessionTitle = action.text) }

            is TimerAction.OpenAddNoteDialog -> {
                if (_state.value.isRunning) _state.update { it.copy(isAddNoteDialogOpen = true) }
            }
            is TimerAction.CloseAddNoteDialog -> _state.update { it.copy(isAddNoteDialogOpen = false, dialogNoteText = "") }
            is TimerAction.UpdateDialogNoteText -> _state.update { it.copy(dialogNoteText = action.text) }
            is TimerAction.UpdateDialogNoteColor -> _state.update { it.copy(dialogNoteColor = action.color) }
            is TimerAction.SaveNote -> saveNote()

            // NEW: Multi-Select Category Logic
            is TimerAction.ToggleCategory -> {
                _state.update { currentState ->
                    val currentList = currentState.selectedCategories
                    // If the category is already in the list, remove it. If it's not, add it!
                    val newList = if (currentList.any { it.id == action.category.id }) {
                        currentList.filter { it.id != action.category.id }
                    } else {
                        currentList + action.category
                    }
                    currentState.copy(selectedCategories = newList)
                }
            }
            is TimerAction.SkipCategoriesAndSave -> executeSave(emptyList())
            is TimerAction.ConfirmCategoriesAndSave -> executeSave(_state.value.selectedCategories)
            is TimerAction.OpenCreateTagDialog -> _state.update { it.copy(isCreateTagDialogOpen = true) }
            is TimerAction.CloseCreateTagDialog -> _state.update { it.copy(isCreateTagDialogOpen = false, newTagDescription = "") }
            is TimerAction.UpdateNewTagName -> _state.update { it.copy(newTagName = action.name) }
            is TimerAction.UpdateNewTagColor -> _state.update { it.copy(newTagColor = action.color) }
            is TimerAction.UpdateNewTagDescription -> _state.update { it.copy(newTagDescription = action.description) } // <-- Handles desc update
            is TimerAction.SaveNewTag -> {
                val name = _state.value.newTagName
                if (name.isNotBlank()) {
                    val tagIdToSave = _state.value.tagBeingEditedId ?: platformSpecificId() // Use existing ID if editing!
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
                // If they click the same folder again, deselect it (make it null). Otherwise, select it.
                val newSelection = if (_state.value.selectedFolder?.id == action.folder?.id) null else action.folder
                _state.update { it.copy(selectedFolder = newSelection) }
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
                    newTagDescription = action.tag.description ?: "", // <-- PRE-FILLS IT
                    newTagColor = action.tag.color
                ) }
            }
        }
    }

    private fun startTimer() {
        if (_state.value.isRunning) return

        if (_state.value.timelineEvents.isNotEmpty()) {
            val typedTitle = _state.value.sessionTitle
            val pickedCategories = _state.value.selectedCategories
            val currentTags = _state.value.availableTags
            val currentFolders = _state.value.availableFolders
            val pickedFolder = _state.value.selectedFolder

            _state.update {
                TimerState(
                    sessionTitle = typedTitle,
                    selectedCategories = pickedCategories,
                    availableTags = currentTags,
                    availableFolders = currentFolders,
                    selectedFolder = pickedFolder
                )
            }

            activeSeconds = 0
            currentPauseSeconds = 0
            totalPauseSeconds = 0
        }

        addEventToTimeline("Session Started", EventType.START)
        _state.update { it.copy(isRunning = true, isPaused = false) }

        // Tells Android to fire up the persistent notification!
        com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.startService()

        startTicking()
    }

    private fun pauseTimer() {
        if (!_state.value.isRunning || _state.value.isPaused) return
        addEventToTimeline("Paused", EventType.PAUSE)

        // NEW: Ensure currentPauseTime shows 00:00:00 immediately
        _state.update { it.copy(isPaused = true, currentPauseTime = formatTime(currentPauseSeconds)) }
    }

    private fun resumeTimer() {
        if (!_state.value.isPaused) return
        val pauseDurationStr = formatTime(currentPauseSeconds)
        addEventToTimeline("Resumed (Break was $pauseDurationStr)", EventType.RESUME)
        currentPauseSeconds = 0
        _state.update { it.copy(isPaused = false) }
    }

    private fun endTimer() {
        if (!_state.value.isRunning && !_state.value.isPaused) return

        // 1. KILL THE LOOP IMMEDITELY
        timerJob?.cancel()
        // 2. KILL THE ANDROID SERVICE NOTIFICATION
        com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.stopService()

        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        addEventToTimeline("Session Ended: $title", EventType.END)

        // 3. FORCE THE UI BACK TO THE "START" BUTTON
        _state.update { it.copy(isRunning = false, isPaused = false) }

        if (_state.value.selectedCategories.isNotEmpty()) {
            executeSave(_state.value.selectedCategories)
        } else {
            _state.update { it.copy(isCategoryPopupOpen = true) }
        }
    }

    private fun executeSave(categories: List<TimenoteFolder>) {
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        val finalDuration = formatTime(activeSeconds + totalPauseSeconds)
        val waypointCount = _state.value.timelineEvents.size

        val timestampId = platformSpecificId() // Get the ID/Timestamp once

        val newTimenote = com.oblutack.timenote.feature_history.domain.Timenote(
            id = timestampId,
            folderId = _state.value.selectedFolder?.id,
            title = title,
            description = "",
            duration = finalDuration,
            activeSeconds = activeSeconds,          // <--- THE REAL DATA
            pauseSeconds = totalPauseSeconds,       // <--- THE REAL DATA
            createdAt = timestampId.toLongOrNull() ?: 0L, // <--- THE REAL TIMESTAMP
            tags = categories,
            timelineEvents = _state.value.timelineEvents
        )

        com.oblutack.timenote.data.repository.SessionRepository.saveTimenote(newTimenote)

        _state.update { it.copy(
            isCategoryPopupOpen = false,
            selectedCategories = categories,
            lastSessionTitle = title,
            sessionTitle = ""
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
    }

    private fun startTicking() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                if (_state.value.isPaused) {
                    currentPauseSeconds++
                    totalPauseSeconds++
                    val formattedPause = formatTime(currentPauseSeconds)

                    _state.update { it.copy(currentPauseTime = formattedPause) }

                    // Update Notification for Paused State
                    com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.updateNotification(
                        title = "Paused",
                        time = formattedPause
                    )
                } else {
                    activeSeconds++
                    val formattedActive = formatTime(activeSeconds + totalPauseSeconds)

                    _state.update { it.copy(displayTime = formattedActive) }

                    // Update Notification for Active State
                    com.oblutack.timenote.feature_timer.domain.ServiceLocator.timerServiceManager?.updateNotification(
                        title = _state.value.sessionTitle.ifBlank { "Timenote Active" },
                        time = formattedActive
                    )
                }
            }
        }
    }

    private fun addEventToTimeline(title: String, type: EventType, color: Color? = null) {
        val totalElapsedSeconds = activeSeconds + totalPauseSeconds

        val newEvent = TimelineEvent(
            id = platformSpecificId(),
            title = title,
            timestamp = formatTime(totalElapsedSeconds),
            type = type,
            isLastItem = false,
            color = color
        )

        _state.update { currentState ->
            val updatedList = listOf(newEvent) + currentState.timelineEvents
            val finalizedList = updatedList.mapIndexed { index, event ->
                event.copy(isLastItem = index == updatedList.lastIndex)
            }
            currentState.copy(timelineEvents = finalizedList)
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    private fun platformSpecificId(): String = com.oblutack.timenote.getCurrentTimeMillis().toString()
}

sealed class TimerAction {
    data object Start : TimerAction()
    data object Pause : TimerAction()
    data object Resume : TimerAction()
    data object End : TimerAction()
    data class UpdateSessionTitle(val text: String) : TimerAction()

    // Dialog Actions
    data object OpenAddNoteDialog : TimerAction()
    data object CloseAddNoteDialog : TimerAction()
    data class UpdateDialogNoteText(val text: String) : TimerAction()
    data class UpdateDialogNoteColor(val color: Color) : TimerAction()
    data object SaveNote : TimerAction()

    // Multi-select Category Actions
    data class ToggleCategory(val category: TimenoteFolder) : TimerAction()
    data object SkipCategoriesAndSave : TimerAction()
    data object ConfirmCategoriesAndSave : TimerAction()

    data object OpenCreateTagDialog : TimerAction()
    data object CloseCreateTagDialog : TimerAction()
    data class UpdateNewTagName(val name: String) : TimerAction()
    data class UpdateNewTagColor(val color: Color) : TimerAction()
    data object SaveNewTag : TimerAction()

    data class UpdateNewTagDescription(val description: String) : TimerAction()

    data object ToggleTagMenu : TimerAction()

    data object ToggleTagsRowVisibility : TimerAction()

    data class SelectFolder(val folder: com.oblutack.timenote.feature_history.domain.ProjectFolder?) : TimerAction()

    data object OpenManageTagsSheet : TimerAction()
    data object CloseManageTagsSheet : TimerAction()
    data class DeleteTag(val tagId: String) : TimerAction()
    data class EditTag(val tag: TimenoteFolder) : TimerAction()
}