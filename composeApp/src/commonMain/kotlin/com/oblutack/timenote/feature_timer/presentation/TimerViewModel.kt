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
import com.oblutack.timenote.feature_history.domain.PastSession

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

    // Internal counters
    private var activeSeconds = 0
    private var currentPauseSeconds = 0 // Tracks the current break length
    private var totalPauseSeconds = 0   // Accumulates ALL breaks for the chronological timeline

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

        if (activeSeconds > 0 && !_state.value.isPaused) {
            _state.update { TimerState() }
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

        // --- NEW: Save the session to our Repository! ---
        val finalDuration = formatTime(activeSeconds + totalPauseSeconds)
        val waypointCount = _state.value.timelineEvents.size

        val newSession = PastSession(
            id = platformSpecificId(),
            title = title,
            description = "$waypointCount waypoints recorded",
            duration = finalDuration,
            tags = emptyList() // We will add the ability to select Folders/Tags later!
        )

        SessionRepository.saveSession(newSession)
        println("Session Saved to Repository: $title")
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