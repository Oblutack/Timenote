package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.feature_history.domain.Timenote
import kotlinx.coroutines.flow.StateFlow

class TrashViewModel : ViewModel() {
    val deletedTimenotes: StateFlow<List<Timenote>> = SessionRepository.deletedTimenotes
    val deletedFolders: StateFlow<List<ProjectFolder>> = SessionRepository.deletedFolders

    fun restoreTimenote(id: String) {
        SessionRepository.restoreTimenote(id)
    }

    fun hardDeleteTimenote(id: String) {
        SessionRepository.hardDeleteTimenote(id)
    }

    fun restoreFolder(id: String) {
        SessionRepository.restoreFolder(id)
    }

    fun hardDeleteFolder(id: String) {
        SessionRepository.hardDeleteFolder(id)
    }

    fun emptyTrash() {
        SessionRepository.emptyTrash()
    }
}

