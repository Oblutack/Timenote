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
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val sessionTitle: String = "",
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
    val newTagColor: Color = Color(0xFF4FA8F9)
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
                            sessionTitle = lastNote.title,
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
            is TimerAction.CloseCreateTagDialog -> _state.update { it.copy(isCreateTagDialogOpen = false, newTagName = "") }
            is TimerAction.UpdateNewTagName -> _state.update { it.copy(newTagName = action.name) }
            is TimerAction.UpdateNewTagColor -> _state.update { it.copy(newTagColor = action.color) }
            is TimerAction.SaveNewTag -> {
                val name = _state.value.newTagName
                if (name.isNotBlank()) {
                    val newTag = TimenoteFolder(
                        id = platformSpecificId(),
                        name = name,
                        sessionCount = 0,
                        color = _state.value.newTagColor
                    )
                    com.oblutack.timenote.data.repository.SessionRepository.saveTag(newTag)
                }
                _state.update { it.copy(isCreateTagDialogOpen = false, newTagName = "") }
            }
        }
    }

    private fun startTimer() {
        if (_state.value.isRunning) return

        if (_state.value.timelineEvents.isNotEmpty()) {
            val typedTitle = _state.value.sessionTitle
            val pickedCategories = _state.value.selectedCategories

            _state.update { TimerState(sessionTitle = typedTitle, selectedCategories = pickedCategories) }
            activeSeconds = 0
            currentPauseSeconds = 0
            totalPauseSeconds = 0
        }

        addEventToTimeline("Session Started", EventType.START)
        _state.update { it.copy(isRunning = true, isPaused = false) }
        startTicking()
    }

    private fun pauseTimer() {
        if (!_state.value.isRunning || _state.value.isPaused) return
        addEventToTimeline("Paused", EventType.PAUSE)
        _state.update { it.copy(isPaused = true) }
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

        timerJob?.cancel()
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        addEventToTimeline("Session Ended: $title", EventType.END)
        _state.update { it.copy(isRunning = false, isPaused = false) }

        // --- MULTI-SELECT DISCLOSURE MAGIC ---
        if (_state.value.selectedCategories.isNotEmpty()) {
            // They picked at least one category. Save instantly.
            executeSave(_state.value.selectedCategories)
        } else {
            // They picked none. Pop open the bottom sheet to ask them!
            _state.update { it.copy(isCategoryPopupOpen = true) }
        }
    }

    private fun executeSave(categories: List<TimenoteFolder>) {
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        val finalDuration = formatTime(activeSeconds + totalPauseSeconds)
        val waypointCount = _state.value.timelineEvents.size

        val newTimenote = com.oblutack.timenote.feature_history.domain.Timenote(
            id = platformSpecificId(),
            title = title,
            description = "$waypointCount waypoints recorded",
            duration = finalDuration,
            tags = categories, // Saving the full list!
            timelineEvents = _state.value.timelineEvents
        )

        com.oblutack.timenote.data.repository.SessionRepository.saveTimenote(newTimenote)

        _state.update { it.copy(isCategoryPopupOpen = false, selectedCategories = categories) }
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
                } else {
                    activeSeconds++
                    _state.update { it.copy(displayTime = formatTime(activeSeconds + totalPauseSeconds)) }
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
}