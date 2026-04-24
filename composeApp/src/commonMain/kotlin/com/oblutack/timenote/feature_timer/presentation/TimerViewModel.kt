package com.oblutack.timenote.feature_timer.presentation

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

// This data class represents everything the UI needs to draw the screen
data class TimerState(
    val displayTime: String = "00:00:00",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentNote: String = "",
    val timelineEvents: List<TimelineEvent> = emptyList()
)

class TimerViewModel : ViewModel() {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var timerJob: Job? = null

    // Internal counters (in seconds)
    private var activeSeconds = 0
    private var pauseSeconds = 0

    fun onAction(action: TimerAction) {
        when (action) {
            is TimerAction.Start -> startTimer()
            is TimerAction.Pause -> pauseTimer()
            is TimerAction.Resume -> resumeTimer()
            is TimerAction.End -> endTimer()
            is TimerAction.UpdateNoteInput -> _state.update { it.copy(currentNote = action.text) }
            is TimerAction.AddNote -> addNote()
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
        // The ticking coroutine will automatically switch to counting `pauseSeconds`
    }

    private fun resumeTimer() {
        if (!_state.value.isPaused) return

        // Log how long the pause was!
        val pauseDurationStr = formatTime(pauseSeconds)
        addEventToTimeline("Resumed (Break was $pauseDurationStr)", EventType.RESUME)

        pauseSeconds = 0 // Reset pause counter for the next potential break
        _state.update { it.copy(isPaused = false) }
    }

    private fun endTimer() {
        timerJob?.cancel()
        addEventToTimeline("Session Ended", EventType.END)
        _state.update { it.copy(isRunning = false, isPaused = false) }
        // Later, we will trigger a save to the local database here!
    }

    private fun addNote() {
        val noteText = _state.value.currentNote
        if (noteText.isBlank() || !_state.value.isRunning) return

        addEventToTimeline("Note: $noteText", EventType.NOTE)

        // Clear the text field after adding
        _state.update { it.copy(currentNote = "") }
    }

    private fun startTicking() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L) // Wait 1 second

                if (_state.value.isPaused) {
                    pauseSeconds++ // Count the break time
                } else {
                    activeSeconds++ // Count the active work time
                    _state.update { it.copy(displayTime = formatTime(activeSeconds)) }
                }
            }
        }
    }

    private fun addEventToTimeline(title: String, type: EventType) {
        val newEvent = TimelineEvent(
            id = platformSpecificId(),
            title = title,
            timestamp = formatTime(activeSeconds), // Timestamps match the active timer!
            type = type,
            isLastItem = false
        )

        _state.update { currentState ->
            // Add to the top of the list, and update the "isLastItem" flags for the UI lines
            val updatedList = listOf(newEvent) + currentState.timelineEvents
            val finalizedList = updatedList.mapIndexed { index, event ->
                event.copy(isLastItem = index == updatedList.lastIndex)
            }
            currentState.copy(timelineEvents = finalizedList)
        }
    }

    // Helper: Converts seconds to HH:MM:SS
    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    // Temporary ID generator until we add a UUID library
    private fun platformSpecificId(): String = (0..100000).random().toString()
}

// These are the actions the UI can send to the ViewModel
sealed class TimerAction {
    data object Start : TimerAction()
    data object Pause : TimerAction()
    data object Resume : TimerAction()
    data object End : TimerAction()
    data class UpdateNoteInput(val text: String) : TimerAction()
    data object AddNote : TimerAction()
}