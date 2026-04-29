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

    fun saveFolder(id: String? = null, name: String, color: Color) {
        val currentTime = com.oblutack.timenote.getCurrentTimeMillis()
        val folderToSave = if (id == null) {
            ProjectFolder(
                id = currentTime.toString(),
                name = name,
                color = color,
                createdAt = currentTime
            )
        } else {
            val existing = folders.value.find { it.id == id }
            ProjectFolder(
                id = id,
                name = name,
                color = color,
                createdAt = existing?.createdAt ?: currentTime
            )
        }
        SessionRepository.saveFolder(folderToSave)
    }

    fun deleteFolder(id: String) {
        // We need to implement SessionRepository.deleteFolder first but let's assume it exists or will be added
        SessionRepository.deleteFolder(id)
    }
}