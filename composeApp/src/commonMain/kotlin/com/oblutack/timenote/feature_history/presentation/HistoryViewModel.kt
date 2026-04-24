package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import com.oblutack.timenote.data.repository.SessionRepository

class HistoryViewModel : ViewModel() {
    // Updated to use the new timenotes variable
    val sessions = SessionRepository.timenotes
}