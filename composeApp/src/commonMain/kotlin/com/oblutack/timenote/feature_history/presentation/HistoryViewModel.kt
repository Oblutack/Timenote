package com.oblutack.timenote.feature_history.presentation

import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SortOption(val displayName: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    LONGEST("Longest Duration"),
    SHORTEST("Shortest Duration")
}

class HistoryViewModel : ViewModel() {
    val sessions = SessionRepository.timenotes
    val folders = SessionRepository.folders
    val tags = SessionRepository.tags

    // --- FILTER & SORT STATE ---
    private val _selectedFilterTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilterTags = _selectedFilterTags.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NEWEST)
    val sortOption = _sortOption.asStateFlow()

    fun toggleFilterTag(tagId: String) {
        val current = _selectedFilterTags.value.toMutableSet()
        if (current.contains(tagId)) current.remove(tagId) else current.add(tagId)
        _selectedFilterTags.value = current
    }

    fun clearTagFilters() {
        _selectedFilterTags.value = emptySet()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }
    // NEW: Trigger the deletion!
    fun deleteTimenote(id: String) {
        SessionRepository.deleteTimenote(id)
    }

    fun saveFolder(id: String? = null, name: String, description: String? = null, color: Color) { // <-- NEW PARAM
        val currentTime = com.oblutack.timenote.getCurrentTimeMillis()
        val folderToSave = if (id == null) {
            ProjectFolder(
                id = currentTime.toString(),
                name = name,
                description = description, // <-- PASSED IN
                color = color,
                createdAt = currentTime
            )
        } else {
            val existing = folders.value.find { it.id == id }
            ProjectFolder(
                id = id,
                name = name,
                description = description, // <-- PASSED IN
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