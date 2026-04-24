package com.oblutack.timenote.data.repository

import com.oblutack.timenote.feature_history.domain.PastSession
import com.oblutack.timenote.feature_history.domain.mockSessions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// A Singleton object to hold our data while the app is running.
// Later, we will swap this out for a real Room Database or File Storage!
object SessionRepository {

    // We initialize it with your Figma mock data so the screen isn't empty
    private val _sessions = MutableStateFlow<List<PastSession>>(mockSessions)
    val sessions: StateFlow<List<PastSession>> = _sessions.asStateFlow()

    fun saveSession(session: PastSession) {
        // Adds the newest session to the VERY TOP of the list
        _sessions.update { currentList -> listOf(session) + currentList }
    }
}