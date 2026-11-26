package com.oblutack.timenote.feature_timer.presentation

import com.oblutack.timenote.feature_timer.domain.canDeleteWhileRunning
import com.oblutack.timenote.feature_timer.domain.canEditWhileRunning
import com.oblutack.timenote.feature_timer.domain.DeletedWaypoint
import kotlinx.coroutines.flow.asSharedFlow
import com.oblutack.timenote.ui.theme.Palette
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.feature_timer.domain.ActiveSessionBackup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.oblutack.timenote.core.SessionClock
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.getCurrentTimeMillis
import com.oblutack.timenote.feature_timer.domain.AudioRecorder
import com.oblutack.timenote.feature_timer.domain.TimerServiceCommand
import com.oblutack.timenote.feature_timer.domain.TimerServiceManager
import kotlinx.coroutines.flow.Flow
import com.oblutack.timenote.core.newId
import com.oblutack.timenote.core.AudioFiles

/** What the summary shows about a session that just ended. */
data class SessionSummary(val activeSeconds: Int, val pauseSeconds: Int, val noteCount: Int) {
    val totalSeconds: Int get() = activeSeconds + pauseSeconds
}

data class TimerState(
    val displayTime: String = "00:00:00",
    val currentPauseTime: String = "00:00:00",
    /** The big clock in seconds (frozen while paused) and the break in progress: what the ring draws. */
    val elapsedSeconds: Long = 0,
    val pauseSeconds: Long = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val sessionTitle: String = "",
    val lastSessionTitle: String = "",
    val timelineEvents: List<TimelineEvent> = emptyList(),

    val isAddNoteDialogOpen: Boolean = false,
    /** Set while the note sheet edits an existing note instead of adding one. */
    val editingNoteId: String? = null,
    val dialogNoteText: String = "",
    val dialogNoteColor: Color = Palette.Blue,

    val selectedCategories: List<TimenoteFolder> = emptyList(),
    /** Set while the summary is open: the session has ended but is not saved yet. */
    val summary: SessionSummary? = null,
    val availableTags: List<TimenoteFolder> = emptyList(),

    val isCreateTagDialogOpen: Boolean = false,
    val newTagName: String = "",
    val newTagDescription: String = "",
    val newTagColor: Color = Palette.Blue,
    val isTagMenuExpanded: Boolean = false,
    val isTagsRowVisible: Boolean = false,

    val availableFolders: List<ProjectFolder> = emptyList(),
    val selectedFolder: ProjectFolder? = null,

    val isManageTagsSheetOpen: Boolean = false,
    val tagBeingEditedId: String? = null,

    val parentTimenoteId: String? = null,
    val parentWaypointId: String? = null,

    val parentSessionTitle: String? = null,
    val parentWaypointTitle: String? = null,

    val isRecordingVoiceMemo: Boolean = false,
    val voiceMemoDuration: String = "00:00",
    /** True briefly after a voice memo could not start (e.g. no microphone permission). */
    val voiceMemoUnavailable: Boolean = false,

    )

class TimerViewModel(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val timerServiceManager: TimerServiceManager,
    private val audioRecorder: AudioRecorder,
    private val audioFiles: AudioFiles,
    private val serviceCommands: Flow<TimerServiceCommand>,
    private val now: () -> Long = ::getCurrentTimeMillis
) : ViewModel() {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var timerJob: Job? = null

    // --- ABSOLUTE TIME TRACKING ---
    private val clock = SessionClock()

    // Prevents restoring the backup multiple times in a row
    private var hasRestoredBackup = false

    private var frozenActiveSeconds = 0
    private var frozenPauseSeconds = 0
    // When End was pressed: "Keep going" counts the time since then as a break
    private var endedAtMillis = 0L

    // Folder/tag ids from a restored backup. The repository flows may not have emitted yet
    // when the backup is read, so these are resolved whenever the flows deliver data.
    private var pendingRestoreFolderId: String? = null
    private var pendingRestoreCategoryIds: List<String> = emptyList()

    // The note being typed before an edit opened the sheet; it comes back when the edit is over
    private var draftBeforeEdit: Pair<String, Color>? = null

    private val _waypointDeleted = kotlinx.coroutines.flow.MutableSharedFlow<DeletedWaypoint>(extraBufferCapacity = 4)
    /** Emits after a waypoint was deleted, so the screen can offer Undo. */
    val waypointDeleted = _waypointDeleted.asSharedFlow()

    // The folder and tags of the last saved session are put on the chips once, when the app opens
    private var prefilledFromLastSession = false

    // The session currently shown as "your last timenote", so the label can follow its deletion.
    // lastSessionSeen guards against the short gap between saving and the list showing the new note.
    private var lastSessionId: String? = null
    private var lastSessionSeen = false

    // Clears the "voice memo unavailable" hint after a few seconds
    private var hintJob: Job? = null

    // Token of the last branch navigation that was applied (see SetParentLinks)
    private var lastBranchToken: String? = null

    // Token of the last "start in this folder" navigation that was applied (see PrepareInFolder)
    private var lastFolderToken: String? = null

    // Skips redundant notification updates (the loop ticks 4x/sec, the text changes 1x/sec)
    private var lastNotificationKey: String? = null

    // NATIVE JSON PARSER: Ignores unknown data and prevents crashes!
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    init {
        // 1. Listen for past Timenotes (To show "Last Session")
        viewModelScope.launch {
            sessionRepository.timenotes.collect { notes ->
                if (_state.value.isRunning || _state.value.isPaused) return@collect

                if (!prefilledFromLastSession && notes.isNotEmpty()) {
                    prefilledFromLastSession = true
                    prefillFromLastSession(notes.first())
                }

                if (lastSessionId != null && notes.any { it.id == lastSessionId }) lastSessionSeen = true
                // The session shown as "last timenote" was deleted (it had been in the list before)
                val shownSessionGone = lastSessionSeen && notes.none { it.id == lastSessionId }

                if (_state.value.timelineEvents.isEmpty() || shownSessionGone) {
                    val latest = notes.firstOrNull()
                    if (latest != null) {
                        lastSessionId = latest.id
                        lastSessionSeen = true
                        _state.update { it.copy(
                            displayTime = latest.duration,
                            lastSessionTitle = latest.title,
                            sessionTitle = "",
                            timelineEvents = latest.timelineEvents
                        )}
                    } else if (shownSessionGone) {
                        lastSessionId = null
                        lastSessionSeen = false
                        _state.update { it.copy(
                            displayTime = "00:00:00",
                            lastSessionTitle = "",
                            timelineEvents = emptyList()
                        )}
                    }
                }
            }
        }

        // 2. Load Tags
        viewModelScope.launch {
            sessionRepository.tags.collect { dbTags ->
                val restoredIds = pendingRestoreCategoryIds
                // ids stay pending until a tag with that id actually shows up
                pendingRestoreCategoryIds = restoredIds.filter { id -> dbTags.none { it.id == id } }
                _state.update { currentState ->
                    // Re-link selected categories to the fresh DB data (plus any ids awaiting restore)
                    val selectedIds = (currentState.selectedCategories.map { it.id } + restoredIds).distinct()
                    val updatedSelected = selectedIds.mapNotNull { id -> dbTags.find { it.id == id } }
                    currentState.copy(availableTags = dbTags, selectedCategories = updatedSelected)
                }
            }
        }

        // 3. Load Folders
        viewModelScope.launch {
            sessionRepository.folders.collect { dbFolders ->
                val restoredId = pendingRestoreFolderId
                // stays pending until a folder with that id actually shows up
                if (restoredId != null && dbFolders.any { it.id == restoredId }) pendingRestoreFolderId = null
                _state.update { currentState ->
                    val folderId = currentState.selectedFolder?.id ?: restoredId
                    val updatedSelectedFolder = dbFolders.find { it.id == folderId }
                    currentState.copy(availableFolders = dbFolders, selectedFolder = updatedSelectedFolder)
                }
            }
        }

        // 4. RESTORE BACKUP (The Swipe-To-Kill Savior)
        viewModelScope.launch {
            settingsRepository.activeSessionBackupFlow.collect { jsonString ->
                if (jsonString != null && !hasRestoredBackup) {
                    hasRestoredBackup = true
                    try {
                        val backup = jsonParser.decodeFromString<ActiveSessionBackup>(jsonString) // <-- USES NEW PARSER

                        clock.restore(
                            startMillis = backup.startTimeMillis,
                            totalPauseMillis = backup.totalPauseMillis,
                            pauseStartMillis = backup.lastPauseStartTimeMillis,
                            isPaused = backup.isPaused
                        )

                        // Resolve against what the repository already has; anything not loaded yet
                        // is picked up by the tag/folder collectors above.
                        val knownTags = sessionRepository.tags.value
                        val knownFolders = sessionRepository.folders.value
                        val restoredTags = backup.selectedCategoryIds.mapNotNull { id -> knownTags.find { it.id == id } }
                        val restoredFolder = knownFolders.find { it.id == backup.selectedFolderId }
                        pendingRestoreCategoryIds = backup.selectedCategoryIds.filter { id -> knownTags.none { it.id == id } }
                        pendingRestoreFolderId = backup.selectedFolderId?.takeIf { restoredFolder == null }

                        _state.update { it.copy(
                            isRunning = true,
                            isPaused = backup.isPaused,
                            sessionTitle = backup.sessionTitle,
                            timelineEvents = backup.timelineEvents,
                            selectedCategories = restoredTags,
                            selectedFolder = restoredFolder,
                            parentTimenoteId = backup.parentTimenoteId,
                            parentWaypointId = backup.parentWaypointId
                        )}

                        timerServiceManager.startService()
                        startTicking()
                    } catch (e: Exception) {
                        // If anything goes wrong, safely clear the corrupted data without crashing!
                        com.oblutack.timenote.core.logError("TimerViewModel", "Discarding unreadable session backup", e)
                        settingsRepository.saveActiveSession(null)
                    }
                }
            }
        }

        // 5. Listen for Android Notification Buttons
        viewModelScope.launch {
            serviceCommands.collect { command ->
                when (command) {
                    TimerServiceCommand.PAUSE -> onAction(TimerAction.Pause)
                    TimerServiceCommand.RESUME -> onAction(TimerAction.Resume)
                    TimerServiceCommand.END -> onAction(TimerAction.EndFromNotification)
                }
            }
        }
    }

    fun onAction(action: TimerAction) {
        when (action) {
            is TimerAction.Start -> startTimer()
            is TimerAction.Pause -> pauseTimer()
            is TimerAction.Resume -> resumeTimer()
            is TimerAction.End -> endTimer(forceSave = false)
            is TimerAction.EndFromNotification -> endTimer(forceSave = true) // <-- ADDED
            is TimerAction.UpdateSessionTitle -> {
                _state.update { it.copy(sessionTitle = action.text) }
                if (_state.value.isRunning) backupCurrentState()
            }

            is TimerAction.OpenAddNoteDialog -> {
                if (_state.value.isRunning) _state.update { it.copy(isAddNoteDialogOpen = true) }
            }
            is TimerAction.CloseAddNoteDialog -> if (_state.value.editingNoteId != null) finishEdit() else _state.update { it.copy(isAddNoteDialogOpen = false, dialogNoteText = "") }
            // Closed without choosing (swipe down, tap outside, Back): the draft stays for next time
            is TimerAction.DismissAddNoteDialog -> if (_state.value.editingNoteId != null) finishEdit() else _state.update { it.copy(isAddNoteDialogOpen = false) }
            is TimerAction.EditNote -> startEditingNote(action.id)
            is TimerAction.DeleteWaypoint -> deleteWaypoint(action.id)
            is TimerAction.RestoreWaypoint -> restoreWaypoint(action.deleted)
            is TimerAction.UpdateDialogNoteText -> _state.update { it.copy(dialogNoteText = action.text) }
            is TimerAction.UpdateDialogNoteColor -> _state.update { it.copy(dialogNoteColor = action.color) }
            is TimerAction.SaveNote -> saveNote()

            is TimerAction.ToggleCategory -> {
                pendingRestoreCategoryIds = emptyList()
                _state.update { currentState ->
                    val currentList = currentState.selectedCategories
                    val newList = if (currentList.any { it.id == action.category.id }) {
                        currentList.filter { it.id != action.category.id }
                    } else {
                        currentList + action.category
                    }
                    currentState.copy(selectedCategories = newList)
                }
                if (_state.value.isRunning) backupCurrentState()
            }
            is TimerAction.SaveSession -> if (_state.value.summary != null) executeSave(_state.value.selectedCategories)
            is TimerAction.KeepGoing -> keepGoing()
            is TimerAction.DiscardSession -> discardSession()

            is TimerAction.OpenCreateTagDialog -> _state.update { it.copy(isCreateTagDialogOpen = true) }
            is TimerAction.CloseCreateTagDialog -> _state.update { it.copy(isCreateTagDialogOpen = false, newTagName = "", newTagDescription = "") }
            is TimerAction.UpdateNewTagName -> _state.update { it.copy(newTagName = action.name) }
            is TimerAction.UpdateNewTagDescription -> _state.update { it.copy(newTagDescription = action.description) }
            is TimerAction.UpdateNewTagColor -> _state.update { it.copy(newTagColor = action.color) }
            is TimerAction.SaveNewTag -> {
                val name = _state.value.newTagName
                if (name.isNotBlank()) {
                    val tagIdToSave = _state.value.tagBeingEditedId ?: newId()
                    val newTag = TimenoteFolder(
                        id = tagIdToSave,
                        name = name,
                        description = _state.value.newTagDescription,
                        sessionCount = 0,
                        color = _state.value.newTagColor
                    )
                    sessionRepository.saveTag(newTag)
                }
                _state.update { it.copy(isCreateTagDialogOpen = false, newTagName = "", newTagDescription = "", tagBeingEditedId = null) }
            }
            is TimerAction.ToggleTagMenu -> _state.update { it.copy(isTagMenuExpanded = !it.isTagMenuExpanded) }
            is TimerAction.ToggleTagsRowVisibility -> _state.update { it.copy(isTagsRowVisible = !it.isTagsRowVisible) }

            is TimerAction.SelectFolder -> {
                pendingRestoreFolderId = null
                val newSelection = if (_state.value.selectedFolder?.id == action.folder?.id) null else action.folder
                _state.update { it.copy(selectedFolder = newSelection) }
                if (_state.value.isRunning) backupCurrentState()
            }

            is TimerAction.PrepareInFolder -> {
                // The same navigation can deliver its arguments again (tab re-entry, rotation): apply each once
                if (action.token != null) {
                    if (action.token == lastFolderToken) return
                    lastFolderToken = action.token
                }
                if (_state.value.isRunning || _state.value.isPaused) return // a running session keeps its folder
                val folder = sessionRepository.folders.value.find { it.id == action.folderId }
                pendingRestoreFolderId = if (folder == null) action.folderId else null // picked up when folders load
                _state.update { it.copy(selectedFolder = folder) }
            }
            is TimerAction.OpenManageTagsSheet -> _state.update { it.copy(isManageTagsSheetOpen = true) }
            is TimerAction.CloseManageTagsSheet -> _state.update { it.copy(isManageTagsSheetOpen = false) }
            is TimerAction.DeleteTag -> sessionRepository.deleteTag(action.tagId)
            is TimerAction.EditTag -> {
                _state.update { it.copy(
                    isManageTagsSheetOpen = false,
                    isCreateTagDialogOpen = true,
                    tagBeingEditedId = action.tag.id,
                    newTagName = action.tag.name,
                    newTagDescription = action.tag.description ?: "",
                    newTagColor = action.tag.color
                ) }
            }
            is TimerAction.StartVoiceMemo -> {
                if (_state.value.isRunning) {
                    val fileName = "VoiceMemo_${newId()}"
                    val started = audioRecorder.startRecording(fileName)
                    _state.update { it.copy(
                        isRecordingVoiceMemo = started,
                        voiceMemoDuration = "00:00",
                        voiceMemoUnavailable = !started
                    ) }
                    hintJob?.cancel()
                    if (!started) {
                        hintJob = viewModelScope.launch {
                            delay(5_000L)
                            _state.update { it.copy(voiceMemoUnavailable = false) }
                        }
                    }
                }
            }
            is TimerAction.StopVoiceMemo -> {
                val savedPath = audioRecorder.stopRecording()
                _state.update { it.copy(isRecordingVoiceMemo = false) }

                if (savedPath != null && _state.value.isRunning) {
                    addEventToTimeline("Voice Memo attached", EventType.NOTE, null, audioFiles.toRef(savedPath))
                }
            }
            is TimerAction.SetParentLinks -> {
                // The same navigation can deliver these arguments again (tab re-entry, rotation).
                // Apply each branch navigation once, identified by its token.
                if (action.token != null) {
                    if (action.token == lastBranchToken) return
                    lastBranchToken = action.token
                }
                _state.update { it.copy(parentTimenoteId = action.parentId, parentWaypointId = action.waypointId) }

                // Fetch the parent data so the UI can display it!
                if (action.parentId != null) {
                    viewModelScope.launch {
                        val parentSession = sessionRepository.getTimenoteById(action.parentId)
                        if (parentSession != null) {
                            val waypoint = parentSession.timelineEvents.find { it.id == action.waypointId }
                            _state.update { currentState ->
                                currentState.copy(
                                    parentSessionTitle = parentSession.title,
                                    parentWaypointTitle = waypoint?.title
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A new session starts with the folder and tags of the last one (the same work again is one tap on Start). Only when
     * nothing has been chosen yet, so a folder the person came from ("Start session" in a folder) is never replaced.
     */
    private fun prefillFromLastSession(last: Timenote) {
        val now = _state.value
        if (now.isRunning || now.isPaused || now.selectedCategories.isNotEmpty() || now.selectedFolder != null) return

        val knownTags = sessionRepository.tags.value
        val knownFolders = sessionRepository.folders.value
        val tagIds = last.tags.map { it.id }
        val tags = tagIds.mapNotNull { id -> knownTags.find { it.id == id } }
        val folder = knownFolders.find { it.id == last.folderId }
        // When the tag or folder lists have not loaded yet, the collectors above pick the ids up as they arrive
        if (knownTags.isEmpty()) pendingRestoreCategoryIds = tagIds
        if (folder == null && knownFolders.isEmpty()) pendingRestoreFolderId = last.folderId
        _state.update { it.copy(selectedCategories = tags, selectedFolder = folder) }
    }

    private fun startTimer() {
        if (_state.value.isRunning) return

        if (_state.value.timelineEvents.isNotEmpty()) {
            _state.update {
                TimerState(
                    sessionTitle = it.sessionTitle,
                    selectedCategories = it.selectedCategories,
                    availableTags = it.availableTags,
                    availableFolders = it.availableFolders,
                    selectedFolder = it.selectedFolder,
                    // --- THE FIX: DON'T FORGET THE PARENT! ---
                    parentTimenoteId = it.parentTimenoteId,
                    parentWaypointId = it.parentWaypointId,
                    parentSessionTitle = it.parentSessionTitle,
                    parentWaypointTitle = it.parentWaypointTitle
                    // -----------------------------------------
                )
            }
        }

        clock.start(now())
        hasRestoredBackup = true

        addEventToTimeline("Session Started", EventType.START)

        if (_state.value.parentSessionTitle != null && _state.value.parentWaypointTitle != null) {
            val branchMessage = "Branched from: ${_state.value.parentWaypointTitle} (${_state.value.parentSessionTitle})"
            addEventToTimeline(branchMessage, EventType.NOTE, Palette.Purple) // Purple to signify a branch!
        }

        _state.update { it.copy(isRunning = true, isPaused = false) }

        timerServiceManager.startService()
        backupCurrentState()
        startTicking()
    }

    private fun pauseTimer() {
        if (!_state.value.isRunning || _state.value.isPaused) return

        clock.pause(now())
        addEventToTimeline("Paused", EventType.PAUSE)

        _state.update { it.copy(isPaused = true) }
        backupCurrentState()
    }

    private fun resumeTimer() {
        if (!_state.value.isPaused) return

        val pauseDurationMillis = clock.resume(now())

        val pauseDurationStr = formatTime((pauseDurationMillis / 1000).toInt())
        addEventToTimeline("Resumed (Break was $pauseDurationStr)", EventType.RESUME)

        _state.update { it.copy(isPaused = false) }
        backupCurrentState()
    }

    private fun endTimer(forceSave: Boolean = false) {
        if (!_state.value.isRunning && !_state.value.isPaused) return

        timerJob?.cancel()
        timerServiceManager.stopService()

        // --- THE FIX: Freeze the math right now, BEFORE we change the state! ---
        val now = now()
        frozenActiveSeconds = clock.activeSeconds(now)
        frozenPauseSeconds = clock.pauseSeconds(now)
        // -----------------------------------------------------------------------

        endedAtMillis = now

        // 1. SET TO FALSE IMMEDIATELY so backups are blocked!
        _state.update { it.copy(isRunning = false, isPaused = false) }

        // 2. Add the timeline event (Now it won't back up)
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }
        addEventToTimeline("Session Ended: $title", EventType.END)

        // The backup of the running session stays until the session is saved or discarded: if the app is killed while
        // the summary is open, the session comes back instead of being lost.
        if (forceSave) {
            executeSave(_state.value.selectedCategories)
        } else {
            _state.update { it.copy(summary = SessionSummary(frozenActiveSeconds, frozenPauseSeconds, noteCount(it.timelineEvents))) }
        }
    }

    private fun noteCount(events: List<TimelineEvent>): Int =
        events.count { it.type == EventType.NOTE && !it.title.startsWith("Branched from:") }

    /**
     * Takes the end back. The time between End and now counts as a break (the session was not worked on), unless the
     * session was paused when it ended: then the pause simply goes on.
     */
    private fun keepGoing() {
        if (_state.value.summary == null) return
        val current = now()
        val wasPaused = clock.isPaused
        val timeline = _state.value.timelineEvents.let { events -> if (events.firstOrNull()?.type == EventType.END) events.drop(1) else events }
        _state.update { it.copy(timelineEvents = timeline, summary = null, isRunning = true, isPaused = wasPaused) }

        if (!wasPaused) {
            clock.pause(endedAtMillis)
            val gap = clock.resume(current)
            addEventToTimeline("Resumed (Break was ${formatTime((gap / 1000).toInt())})", EventType.RESUME)
        }
        timerServiceManager.startService()
        backupCurrentState()
        startTicking()
    }

    /** Throws the ended session away: nothing is saved, the backup is removed, the chips stay for the next one. */
    private fun discardSession() {
        if (_state.value.summary == null) return
        viewModelScope.launch { settingsRepository.saveActiveSession(null) }
        val latest = sessionRepository.timenotes.value.firstOrNull()
        _state.update { it.copy(
            summary = null,
            sessionTitle = "",
            displayTime = latest?.duration ?: "00:00:00",
            lastSessionTitle = latest?.title ?: "",
            timelineEvents = latest?.timelineEvents ?: emptyList(),
            parentTimenoteId = null,
            parentWaypointId = null,
            parentSessionTitle = null,
            parentWaypointTitle = null
        ) }
    }

    private fun executeSave(categories: List<TimenoteFolder>) {
        val title = _state.value.sessionTitle.ifBlank { "Untitled Session" }

        // Saved: the running-session backup is no longer needed
        viewModelScope.launch { settingsRepository.saveActiveSession(null) }

        // The title may have been changed in the summary
        val events = _state.value.timelineEvents.map { e ->
            if (e.type == EventType.END) e.copy(title = "Session Ended: $title") else e
        }

        val timestampId = newId()
        lastSessionId = timestampId
        lastSessionSeen = false

        val newTimenote = Timenote(
            id = timestampId,
            folderId = _state.value.selectedFolder?.id,
            title = title,
            description = "",
            // --- THE FIX: Just use the math we already froze! ---
            duration = formatTime(frozenActiveSeconds + frozenPauseSeconds),
            activeSeconds = frozenActiveSeconds,
            pauseSeconds = frozenPauseSeconds,
            createdAt = now(),
            // ----------------------------------------------------
            tags = categories,
            timelineEvents = events,
            parentTimenoteId = _state.value.parentTimenoteId,
            parentWaypointId = _state.value.parentWaypointId
        )

        sessionRepository.saveTimenote(newTimenote)

        _state.update { it.copy(
            summary = null,
            timelineEvents = events,
            selectedCategories = categories,
            lastSessionTitle = title,
            sessionTitle = "",
            // The branch is finished; clear the display titles too, or the banner stays up and the
            // next unrelated session would get a bogus "Branched from" waypoint.
            parentTimenoteId = null,
            parentWaypointId = null,
            parentSessionTitle = null,
            parentWaypointTitle = null
        ) }
    }

    /** Opens the note sheet on an existing note; what was being typed is kept for afterwards. */
    private fun startEditingNote(id: String) {
        val current = _state.value
        if (!current.isRunning || current.editingNoteId != null) return
        val event = current.timelineEvents.find { it.id == id } ?: return
        if (!event.canEditWhileRunning()) return
        draftBeforeEdit = current.dialogNoteText to current.dialogNoteColor
        _state.update { it.copy(
            editingNoteId = id,
            dialogNoteText = event.title.removePrefix("Note: "),
            dialogNoteColor = event.color ?: Palette.Blue,
            isAddNoteDialogOpen = true
        ) }
    }

    /** Closes the note sheet after an edit (saved or not) and brings back the note that was being typed. */
    private fun finishEdit() {
        val draft = draftBeforeEdit
        draftBeforeEdit = null
        _state.update { it.copy(
            editingNoteId = null,
            isAddNoteDialogOpen = false,
            dialogNoteText = draft?.first ?: "",
            dialogNoteColor = draft?.second ?: Palette.Blue
        ) }
    }

    private fun deleteWaypoint(id: String) {
        val current = _state.value
        if (!current.isRunning) return
        val index = current.timelineEvents.indexOfFirst { it.id == id }
        if (index < 0) return
        val event = current.timelineEvents[index]
        if (!event.canDeleteWhileRunning()) return
        val deleted = DeletedWaypoint(event, olderCount = current.timelineEvents.size - index - 1)
        _state.update { it.copy(timelineEvents = reflag(it.timelineEvents.filterNot { e -> e.id == id })) }
        backupCurrentState()
        _waypointDeleted.tryEmit(deleted)
    }

    /** Puts a deleted waypoint back where it was, even when newer entries were added since. */
    private fun restoreWaypoint(deleted: DeletedWaypoint) {
        if (!_state.value.isRunning) return
        if (_state.value.timelineEvents.any { it.id == deleted.event.id }) return
        _state.update { current ->
            val position = (current.timelineEvents.size - deleted.olderCount).coerceIn(0, current.timelineEvents.size)
            val events = current.timelineEvents.toMutableList().apply { add(position, deleted.event) }
            current.copy(timelineEvents = reflag(events))
        }
        backupCurrentState()
    }

    private fun reflag(events: List<TimelineEvent>): List<TimelineEvent> =
        events.mapIndexed { index, event -> event.copy(isLastItem = index == events.lastIndex) }

    private fun saveNote() {
        val noteText = _state.value.dialogNoteText
        if (noteText.isBlank() || !_state.value.isRunning) return

        val editingId = _state.value.editingNoteId
        if (editingId != null) {
            val color = _state.value.dialogNoteColor
            _state.update { s -> s.copy(timelineEvents = s.timelineEvents.map { e ->
                if (e.id == editingId) e.copy(title = "Note: $noteText", color = color) else e
            }) }
            finishEdit()
            backupCurrentState()
            return
        }

        addEventToTimeline("Note: $noteText", EventType.NOTE, _state.value.dialogNoteColor)

        _state.update { it.copy(
            isAddNoteDialogOpen = false,
            dialogNoteText = "",
            dialogNoteColor = Palette.Blue
        ) }
        backupCurrentState()
    }

    private fun startTicking() {
        timerJob?.cancel()
        lastNotificationKey = null
        timerJob = viewModelScope.launch {
            while (true) {
                delay(250L)

                val now = now()

                if (_state.value.isPaused) {
                    // 1. The Pause Timer (Ticking)
                    val formattedPause = formatTime((clock.currentPauseMillis(now) / 1000).toInt())

                    // 2. The Main Timer (FROZEN)
                    // We freeze it at the exact moment you hit the Pause button
                    val formattedFrozenTotal = formatTime((clock.displayElapsedMillis(now) / 1000).toInt())

                    _state.update { it.copy(
                        currentPauseTime = formattedPause,
                        displayTime = formattedFrozenTotal, // <-- Now it stops moving!
                        elapsedSeconds = clock.displayElapsedMillis(now) / 1000,
                        pauseSeconds = clock.currentPauseMillis(now) / 1000
                    ) }

                    val pausedKey = "paused|$formattedPause"
                    if (pausedKey != lastNotificationKey) {
                        lastNotificationKey = pausedKey
                        timerServiceManager.updateNotification(
                            title = "Paused", timeText = formattedPause, baseMillis = 0L, isPaused = true
                        )
                    }
                } else {
                    // 1. The Main Timer (Ticking Total Time)
                    val totalElapsedMillis = clock.displayElapsedMillis(now)
                    val formattedTotal = formatTime((totalElapsedMillis / 1000).toInt())

                    _state.update { it.copy(displayTime = formattedTotal, elapsedSeconds = totalElapsedMillis / 1000, pauseSeconds = 0) }

                    // NATIVE MATH: Tell Android OS to count the Total Time too!
                    val baseTimeForOS = now() - totalElapsedMillis
                    val notificationTitle = _state.value.sessionTitle.ifBlank { "Timenote Active" }

                    val runningKey = "running|$notificationTitle|$formattedTotal"
                    if (runningKey != lastNotificationKey) {
                        lastNotificationKey = runningKey
                        timerServiceManager.updateNotification(
                            title = notificationTitle,
                            timeText = formattedTotal,
                            baseMillis = baseTimeForOS,
                            isPaused = false
                        )
                    }
                }
            }
        }
    }

    private fun addEventToTimeline(title: String, type: EventType, color: Color? = null, audioPath: String? = null) {
        val now = now()
        val totalElapsedSeconds = clock.elapsedSeconds(now)

        val newEvent = TimelineEvent(
            id = newId(),
            title = title,
            timestamp = formatTime(totalElapsedSeconds),
            type = type,
            isLastItem = false,
            color = color,
            audioPath = audioPath // <-- PASS THE PATH HERE
        )

        _state.update { currentState ->
            val updatedList = listOf(newEvent) + currentState.timelineEvents
            val finalizedList = updatedList.mapIndexed { index, event ->
                event.copy(isLastItem = index == updatedList.lastIndex)
            }
            currentState.copy(timelineEvents = finalizedList)
        }

        backupCurrentState()
    }

    private fun backupCurrentState() {

        if (!_state.value.isRunning && !_state.value.isPaused) return

        val backup = ActiveSessionBackup(
            sessionTitle = _state.value.sessionTitle,
            startTimeMillis = clock.startMillis,
            totalPauseMillis = clock.totalPauseMillis,
            lastPauseStartTimeMillis = if (clock.isPaused) clock.pauseStartMillis else null,
            isPaused = _state.value.isPaused,
            timelineEvents = _state.value.timelineEvents,
            selectedFolderId = _state.value.selectedFolder?.id,
            selectedCategoryIds = _state.value.selectedCategories.map { it.id },
            parentTimenoteId = _state.value.parentTimenoteId,
            parentWaypointId = _state.value.parentWaypointId
        )
        val json = jsonParser.encodeToString(backup) // <-- USES NEW PARSER
        viewModelScope.launch { settingsRepository.saveActiveSession(json) }
    }

    private fun formatTime(totalSeconds: Int): String = com.oblutack.timenote.core.formatDuration(totalSeconds)

}

sealed class TimerAction {
    data object Start : TimerAction()
    data object Pause : TimerAction()
    data object Resume : TimerAction()
    data object End : TimerAction()
    data object EndFromNotification : TimerAction()
    data class UpdateSessionTitle(val text: String) : TimerAction()
    data object OpenAddNoteDialog : TimerAction()
    data object CloseAddNoteDialog : TimerAction()
    data object DismissAddNoteDialog : TimerAction()
    data class UpdateDialogNoteText(val text: String) : TimerAction()
    data class UpdateDialogNoteColor(val color: Color) : TimerAction()
    data object SaveNote : TimerAction()
    data class ToggleCategory(val category: TimenoteFolder) : TimerAction()
    /** Saves the ended session with the chosen folder and tags (also what closing the summary does: nothing is ever lost). */
    data object SaveSession : TimerAction()
    /** From the summary: take the end back and carry on. */
    data object KeepGoing : TimerAction()
    /** From the summary: throw the session away (the screen asks first). */
    data object DiscardSession : TimerAction()
    data object OpenCreateTagDialog : TimerAction()
    data object CloseCreateTagDialog : TimerAction()
    data class UpdateNewTagName(val name: String) : TimerAction()
    data class UpdateNewTagDescription(val description: String) : TimerAction()
    data class UpdateNewTagColor(val color: Color) : TimerAction()
    data object SaveNewTag : TimerAction()
    data object ToggleTagMenu : TimerAction()
    data object ToggleTagsRowVisibility : TimerAction()
    data class SelectFolder(val folder: ProjectFolder?) : TimerAction()
    /** Opens the timer ready to record into this folder (from the folder's "Start Session" button). */
    data class PrepareInFolder(val folderId: String, val token: String? = null) : TimerAction()
    data object OpenManageTagsSheet : TimerAction()
    data object CloseManageTagsSheet : TimerAction()
    data class DeleteTag(val tagId: String) : TimerAction()
    data class EditTag(val tag: TimenoteFolder) : TimerAction()
    data class SetParentLinks(val parentId: String?, val waypointId: String?, val token: String? = null) : TimerAction()
    data object StartVoiceMemo : TimerAction()
    data object StopVoiceMemo : TimerAction()
    /** Long-press menu on a note of the running session. */
    data class EditNote(val id: String) : TimerAction()
    data class DeleteWaypoint(val id: String) : TimerAction()
    data class RestoreWaypoint(val deleted: DeletedWaypoint) : TimerAction()
}