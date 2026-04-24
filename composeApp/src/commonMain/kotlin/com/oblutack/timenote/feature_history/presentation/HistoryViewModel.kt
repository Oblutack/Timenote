package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import com.oblutack.timenote.data.repository.SessionRepository

class HistoryViewModel : ViewModel() {
    // This perfectly observes the repository.
    // Anytime the Timer saves a new session, this updates automatically!
    val sessions = SessionRepository.sessions
}