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

data class TimerState(
    val displayTime: String = "00:00:00",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val sessionTitle: String = "", // <-- Replaced currentNote
    val timelineEvents: List<TimelineEvent> = emptyList(),

    // <-- NEW: Dialog State
    val isAddNoteDialogOpen: Boolean = false,
    val dialogNoteText: String = "",
    val dialogNoteColor: Color = Color(0xFF4FA8F9) // Default Blue
)

class TimerViewModel : ViewModel() {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var activeSeconds = 0
    private var pauseSeconds = 0

    fun onAction(action: TimerAction) {
        when (action) {
            is TimerAction.Start -> startTimer()
            is TimerAction.Pause -> pauseTimer()
            is TimerAction.Resume -> resumeTimer()
            is TimerAction.End -> endTimer()
            is TimerAction.UpdateSessionTitle -> _state.update { it.copy(sessionTitle = action.text) }

            // Dialog Actions
            is TimerAction.OpenAddNoteDialog -> {
                if (_state.value.isRunning) _state.update { it.copy(isAddNoteDialogOpen = true) }
            }
            is TimerAction.CloseAddNoteDialog -> _state.update { it.copy(isAddNoteDialogOpen = false, dialogNoteText = "") }
            is TimerAction.UpdateDialogNoteText -> _state.update { it.copy(dialogNoteText = action.text) }
            is TimerAction.UpdateDialogNoteColor -> _state.update { it.copy(dialogNoteColor = action.color) }
            is TimerAction.SaveNote -> saveNote()
        }
    }

    private fun startTimer() {
        if (_state.value.isRunning) return
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
        val pauseDurationStr = formatTime(pauseSeconds)
        addEventToTimeline("Resumed (Break was $pauseDurationStr)", EventType.RESUME)
        pauseSeconds = 0
        _state.update { it.copy(isPaused = false) }
    }

    private fun endTimer() {
        timerJob?.cancel()
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }

        // TODO: In our next major step, we will take the title, duration,
        // and timelineEvents and save them to the local Database right here!
        println("Auto-saving session: $title with ${_state.value.timelineEvents.size} events.")

        // Reset the ENTIRE screen back to factory defaults for the next session
        _state.update { TimerState() }

        // Reset our internal hidden counters
        activeSeconds = 0
        pauseSeconds = 0
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
                    pauseSeconds++
                } else {
                    activeSeconds++
                    _state.update { it.copy(displayTime = formatTime(activeSeconds)) }
                }
            }
        }
    }

    private fun addEventToTimeline(title: String, type: EventType, color: Color? = null) {
        val newEvent = TimelineEvent(
            id = platformSpecificId(),
            title = title,
            timestamp = formatTime(activeSeconds),
            type = type,
            isLastItem = false,
            color = color // Apply color
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

    private fun platformSpecificId(): String = (0..100000).random().toString()
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
}