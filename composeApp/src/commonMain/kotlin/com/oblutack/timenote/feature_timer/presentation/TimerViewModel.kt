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
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.Timenote
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

    // NEW: Progressive Disclosure Category State
    val selectedCategory: TimenoteFolder? = null,
    val isCategoryPopupOpen: Boolean = false
)

class TimerViewModel : ViewModel() {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var timerJob: Job? = null

    // Internal counters
    private var activeSeconds = 0
    private var currentPauseSeconds = 0 // Tracks the current break length
    private var totalPauseSeconds = 0   // Accumulates ALL breaks for the chronological timeline

    init {
        viewModelScope.launch {
            com.oblutack.timenote.data.repository.SessionRepository.timenotes.collect { notes ->
                // If the timer is NOT running, and the screen is currently blank...
                if (!_state.value.isRunning && !_state.value.isPaused && _state.value.timelineEvents.isEmpty()) {
                    // Grab the absolute newest note from the database (the first one in the list)
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

            // NEW: Handle Categories
            is TimerAction.SelectCategory -> _state.update { it.copy(selectedCategory = action.category) }
            is TimerAction.SkipCategoryAndSave -> executeSave(null)
            is TimerAction.ConfirmCategoryAndSave -> executeSave(action.category)
        }
    }

    private fun startTimer() {
        if (_state.value.isRunning) return

        if (activeSeconds > 0 && !_state.value.isPaused) {
            val typedTitle = _state.value.sessionTitle
            val pickedCategory = _state.value.selectedCategory // Preserve the category!

            _state.update { TimerState(sessionTitle = typedTitle, selectedCategory = pickedCategory) }
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

        currentPauseSeconds = 0 // Reset for the next break
        _state.update { it.copy(isPaused = false) }
    }

    private fun endTimer() {
        if (!_state.value.isRunning && !_state.value.isPaused) return

        timerJob?.cancel()
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        addEventToTimeline("Session Ended: $title", EventType.END)
        _state.update { it.copy(isRunning = false, isPaused = false) }

        // --- THE PROGRESSIVE DISCLOSURE MAGIC ---
        if (_state.value.selectedCategory != null) {
            // Power user! They already picked a category. Save instantly.
            executeSave(_state.value.selectedCategory)
        } else {
            // They forgot. Pop open the bottom sheet to ask them!
            _state.update { it.copy(isCategoryPopupOpen = true) }
        }
    }

    private fun executeSave(category: TimenoteFolder?) {
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        val finalDuration = formatTime(activeSeconds + totalPauseSeconds)
        val waypointCount = _state.value.timelineEvents.size

        // If they picked a category, put it in the list. Otherwise, leave it empty.
        val tagsList = if (category != null) listOf(category) else emptyList()

        val newTimenote = com.oblutack.timenote.feature_history.domain.Timenote(
            id = platformSpecificId(),
            title = title,
            description = "$waypointCount waypoints recorded",
            duration = finalDuration,
            tags = tagsList,
            timelineEvents = _state.value.timelineEvents
        )

        com.oblutack.timenote.data.repository.SessionRepository.saveTimenote(newTimenote)

        // Close the popup and update the state so the screen shows the category they just picked!
        _state.update { it.copy(isCategoryPopupOpen = false, selectedCategory = category) }
    }

    private fun saveNote() {
        val noteText = _state.value.dialogNoteText
        if (noteText.isBlank() || !_state.value.isRunning) return

        // Pass the custom color from the dialog!
        addEventToTimeline("Note: $noteText", EventType.NOTE, _state.value.dialogNoteColor)

        // Close dialog and reset
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
                    totalPauseSeconds++ // This keeps the chronological timeline moving!
                } else {
                    activeSeconds++
                    _state.update { it.copy(displayTime = formatTime(activeSeconds + totalPauseSeconds)) }
                }
            }
        }
    }

    private fun addEventToTimeline(title: String, type: EventType, color: Color? = null) {
        // Use totalPauseSeconds so the timeline always moves forward chronologically!
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

    // NEW: Category Actions
    data class SelectCategory(val category: TimenoteFolder) : TimerAction()
    data object SkipCategoryAndSave : TimerAction()
    data class ConfirmCategoryAndSave(val category: TimenoteFolder) : TimerAction()
}