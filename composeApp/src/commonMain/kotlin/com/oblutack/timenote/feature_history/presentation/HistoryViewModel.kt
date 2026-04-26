package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import com.oblutack.timenote.data.repository.SessionRepository

class HistoryViewModel : ViewModel() {
    val sessions = SessionRepository.timenotes

    // NEW: Trigger the deletion!
    fun deleteTimenote(id: String) {
        SessionRepository.deleteTimenote(id)
    }
}