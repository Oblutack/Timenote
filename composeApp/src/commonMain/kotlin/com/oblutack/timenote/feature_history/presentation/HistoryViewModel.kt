package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.ProjectFolder

class HistoryViewModel : ViewModel() {
    val sessions = SessionRepository.timenotes
    val folders = SessionRepository.folders

    // NEW: Trigger the deletion!
    fun deleteTimenote(id: String) {
        SessionRepository.deleteTimenote(id)
    }

    fun createFolder(name: String, color: Color) {
        val currentTime = com.oblutack.timenote.getCurrentTimeMillis()
        val newFolder = ProjectFolder(
            id = currentTime.toString(),
            name = name,
            color = color,
            createdAt = currentTime
        )
        SessionRepository.saveFolder(newFolder)
    }
}