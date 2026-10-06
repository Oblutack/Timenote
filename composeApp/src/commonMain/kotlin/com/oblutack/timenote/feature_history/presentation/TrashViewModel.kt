package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.feature_history.domain.Timenote
import kotlinx.coroutines.flow.StateFlow

class TrashViewModel(private val sessionRepository: SessionRepository) : ViewModel() {
    val deletedTimenotes: StateFlow<List<Timenote>> = sessionRepository.deletedTimenotes
    val deletedFolders: StateFlow<List<ProjectFolder>> = sessionRepository.deletedFolders

    fun restoreTimenote(id: String) {
        sessionRepository.restoreTimenote(id)
    }

    fun hardDeleteTimenote(id: String) {
        sessionRepository.hardDeleteTimenote(id)
    }

    fun restoreFolder(id: String) {
        sessionRepository.restoreFolder(id)
    }

    fun hardDeleteFolder(id: String) {
        sessionRepository.hardDeleteFolder(id)
    }

    fun emptyTrash() {
        sessionRepository.emptyTrash()
    }
}

