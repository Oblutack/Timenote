package com.oblutack.timenote.data.repository

import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.mockSessions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object SessionRepository {

    // Uses the new Timenote class
    private val _timenotes = MutableStateFlow<List<Timenote>>(mockSessions)
    val timenotes: StateFlow<List<Timenote>> = _timenotes.asStateFlow()

    fun saveTimenote(timenote: Timenote) {
        _timenotes.update { currentList -> listOf(timenote) + currentList }
    }

    // NEW: A helper function to fetch a single Timenote by its ID for the Details screen!
    fun getTimenoteById(id: String): Timenote? {
        return _timenotes.value.find { it.id == id }
    }
}