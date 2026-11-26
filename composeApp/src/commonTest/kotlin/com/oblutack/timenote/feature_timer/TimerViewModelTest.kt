package com.oblutack.timenote.feature_timer

import androidx.lifecycle.viewModelScope
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.feature_timer.domain.ActiveSessionBackup
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimerServiceCommand
import com.oblutack.timenote.feature_timer.presentation.TimerAction
import com.oblutack.timenote.feature_timer.presentation.TimerViewModel
import com.oblutack.timenote.testutil.FakeAudioRecorder
import com.oblutack.timenote.testutil.FakeDataStore
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.FakeTimerServiceManager
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testTag
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.oblutack.timenote.core.DirectoryAudioFiles
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId

private const val BASE = 1_000_000L

/** Everything a TimerViewModel needs, wired to fakes and to the test scheduler's virtual clock. */
private class Env(private val scope: TestScope) {
    val clock: () -> Long = { BASE + scope.currentTime }
    val sessions = SessionRepository(FakeTimenoteDao(), scope.backgroundScope, FakeDefaultTagsState(), FakeDeviceId(), clock)
    val settings = SettingsRepository(FakeDataStore())
    val service = FakeTimerServiceManager()
    val recorder = FakeAudioRecorder()
    val audioFiles = DirectoryAudioFiles("/fake")
    val commands = MutableSharedFlow<TimerServiceCommand>(extraBufferCapacity = 1)

    private var created: TimerViewModel? = null
    val vm: TimerViewModel
        get() = created ?: TimerViewModel(sessions, settings, service, recorder, audioFiles, commands, clock).also { created = it }

    val state get() = vm.state.value

    fun advance(millis: Long) {
        scope.advanceTimeBy(millis)
        scope.runCurrent()
    }

    fun close() { created?.viewModelScope?.cancel() }
}

private suspend fun TestScope.timerTest(block: suspend Env.() -> Unit) {
    val env = Env(this)
    try {
        env.block()
    } finally {
        env.close()
    }
}

class TimerViewModelTest {

    @Test fun startBeginsSessionAndStartsService() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)

            assertTrue(state.isRunning)
            assertFalse(state.isPaused)
            assertEquals(1, service.startCalls)
            assertEquals(listOf("Session Started"), state.timelineEvents.map { it.title })
            assertNotNull(settings.activeSessionBackupFlow.first(), "a backup is written so the session survives process death")
        }
    }

    @Test fun startingTwiceIsIgnored() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.Start)
            assertEquals(1, service.startCalls)
            assertEquals(1, state.timelineEvents.size)
        }
    }

    @Test fun displayClockFollowsElapsedTime() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(65_000)
            assertEquals("00:01:05", state.displayTime)
        }
    }

    @Test fun pauseAndResumeAreTimestampedOnTheTimeline() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(60_000)
            vm.onAction(TimerAction.Pause)
            assertTrue(state.isPaused)
            advance(30_000)
            vm.onAction(TimerAction.Resume)
            assertFalse(state.isPaused)

            val events = state.timelineEvents // newest first
            assertEquals("Resumed (Break was 00:00:30)", events[0].title)
            assertEquals("00:01:30", events[0].timestamp)
            assertEquals(EventType.PAUSE, events[1].type)
            assertEquals("00:01:00", events[1].timestamp)
        }
    }

    @Test fun displayClockFreezesWhilePausedAndPauseClockRuns() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(60_000)
            vm.onAction(TimerAction.Pause)
            advance(20_000)
            assertEquals("00:01:00", state.displayTime)
            assertEquals("00:00:20", state.currentPauseTime)
        }
    }

    @Test fun theRingGetsTheClockAndTheBreakAsNumbers() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(90_000)
            assertEquals(90L, state.elapsedSeconds)
            assertEquals(0L, state.pauseSeconds)
            vm.onAction(TimerAction.Pause)
            advance(20_000)
            assertEquals(90L, state.elapsedSeconds, "the session clock is frozen while paused")
            assertEquals(20L, state.pauseSeconds)
            vm.onAction(TimerAction.Resume)
            advance(10_000)
            assertEquals(120L, state.elapsedSeconds, "90 s of work, 20 s of break and 10 s more: the clock counts all of it")
            assertEquals(0L, state.pauseSeconds)
        }
    }

    @Test fun endingSavesTimenoteWithActiveAndPauseTime() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.UpdateSessionTitle("Deep work"))
            vm.onAction(TimerAction.Start)
            advance(60_000)
            vm.onAction(TimerAction.Pause)
            advance(30_000)
            vm.onAction(TimerAction.Resume)
            advance(30_000)
            vm.onAction(TimerAction.End)

            assertNotNull(state.summary, "the summary is shown every time a session ends")
            vm.onAction(TimerAction.SaveSession)

            val saved = sessions.timenotes.value.single()
            assertEquals("Deep work", saved.title)
            assertEquals(90, saved.activeSeconds)
            assertEquals(30, saved.pauseSeconds)
            assertEquals("00:02:00", saved.duration)
            assertEquals(1, service.stopCalls)
            assertFalse(state.isRunning)
            assertNull(settings.activeSessionBackupFlow.first(), "the backup is cleared once saved")
        }
    }

    @Test fun untitledSessionGetsADefaultTitle() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(5_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)
            assertEquals("Untitled Session", sessions.timenotes.value.single().title)
        }
    }

    @Test fun endingWhilePausedCountsTheOpenPause() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(60_000)
            vm.onAction(TimerAction.Pause)
            advance(40_000)
            vm.onAction(TimerAction.EndFromNotification)

            val saved = sessions.timenotes.value.single()
            assertEquals(60, saved.activeSeconds)
            assertEquals(40, saved.pauseSeconds)
        }
    }

    @Test fun endingWithTagsSelectedStillShowsTheSummaryAndSavesThemOnSave() = runAppTest {
        timerTest {
            sessions.saveTag(testTag("focus", "Focus"))
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.ToggleCategory(sessions.tags.value.first { it.id == "focus" }))
            advance(10_000)
            vm.onAction(TimerAction.End)

            assertNotNull(state.summary)
            assertTrue(sessions.timenotes.value.isEmpty(), "nothing is saved until the summary is left")
            vm.onAction(TimerAction.SaveSession)
            assertNull(state.summary)
            assertEquals(listOf("Focus"), sessions.timenotes.value.single().tags.map { it.name })
        }
    }

    @Test fun selectedFolderIsSavedWithTheTimenote() = runAppTest {
        timerTest {
            sessions.saveFolder(testFolder("writing"))
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.SelectFolder(sessions.folders.value.single()))
            advance(10_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)
            assertEquals("writing", sessions.timenotes.value.single().folderId)
        }
    }

    // --- the chips are filled in from the last session ---

    private fun com.oblutack.timenote.data.repository.SessionRepository.saveLastSession(folderId: String?, tagIds: List<String>) {
        saveTimenote(
            com.oblutack.timenote.testutil.testNote("last", createdAt = 5_000L)
                .copy(folderId = folderId, tags = tagIds.map { id -> testTag(id, id) })
        )
    }

    @Test fun aNewSessionStartsWithTheFolderAndTagsOfTheLastOne() = runAppTest {
        timerTest {
            sessions.saveFolder(testFolder("writing"))
            sessions.saveTag(testTag("focus", "Focus"))
            sessions.saveLastSession("writing", listOf("focus"))
            vm
            advance(0)

            assertEquals("writing", state.selectedFolder?.id)
            assertEquals(listOf("focus"), state.selectedCategories.map { it.id })
            vm.onAction(TimerAction.Start)
            advance(5_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)
            assertEquals("writing", sessions.timenotes.value.first { it.id != "last" }.folderId, "and they are saved with the new session")
        }
    }

    @Test fun withoutAnyEarlierSessionNothingIsChosen() = runAppTest {
        timerTest {
            vm
            advance(0)
            assertNull(state.selectedFolder)
            assertTrue(state.selectedCategories.isEmpty())
        }
    }

    @Test fun aFolderTheSessionIsStartedFromIsNotReplaced() = runAppTest {
        timerTest {
            sessions.saveFolder(testFolder("writing"))
            sessions.saveFolder(testFolder("reading"))
            sessions.saveLastSession("writing", emptyList())
            vm.onAction(TimerAction.PrepareInFolder("reading", null))
            advance(0)
            assertEquals("reading", state.selectedFolder?.id)
        }
    }

    @Test fun clearingTheChipsIsNotUndoneWhenMoreSessionsArrive() = runAppTest {
        timerTest {
            sessions.saveTag(testTag("focus", "Focus"))
            sessions.saveLastSession(null, listOf("focus"))
            vm
            advance(0)
            assertEquals(1, state.selectedCategories.size)

            vm.onAction(TimerAction.ToggleCategory(state.selectedCategories.single()))
            sessions.saveTimenote(com.oblutack.timenote.testutil.testNote("other", createdAt = 9_000L))
            advance(0)
            assertTrue(state.selectedCategories.isEmpty(), "the person cleared it; the list changing must not bring it back")
        }
    }

    @Test fun aDeletedTagOrFolderOfTheLastSessionIsSimplyLeftOut() = runAppTest {
        timerTest {
            sessions.saveLastSession("gone-folder", listOf("gone-tag"))
            vm
            advance(0)
            assertNull(state.selectedFolder)
            assertTrue(state.selectedCategories.isEmpty())
        }
    }

    @Test fun changingTheChipsWhileRunningUpdatesTheRunningSession() = runAppTest {
        timerTest {
            sessions.saveFolder(testFolder("writing"))
            sessions.saveTag(testTag("focus", "Focus"))
            vm.onAction(TimerAction.Start)
            advance(1_000)
            vm.onAction(TimerAction.SelectFolder(sessions.folders.value.first { it.id == "writing" }))
            vm.onAction(TimerAction.ToggleCategory(sessions.tags.value.first { it.id == "focus" }))
            advance(0)
            val backup = Json { ignoreUnknownKeys = true }.decodeFromString<ActiveSessionBackup>(settings.activeSessionBackupFlow.first()!!)
            assertEquals("writing", backup.selectedFolderId)
            assertEquals(listOf("focus"), backup.selectedCategoryIds)
        }
    }

    // --- the summary at the end of a session (F9, F10) ---

    @Test fun theSummaryShowsFocusBreaksAndNotes() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(60_000)
            vm.onAction(TimerAction.Pause)
            advance(20_000)
            vm.onAction(TimerAction.Resume)
            advance(40_000)
            vm.onAction(TimerAction.OpenAddNoteDialog)
            vm.onAction(TimerAction.UpdateDialogNoteText("an idea"))
            vm.onAction(TimerAction.SaveNote)
            vm.onAction(TimerAction.End)

            val summary = state.summary!!
            assertEquals(100, summary.activeSeconds)
            assertEquals(20, summary.pauseSeconds)
            assertEquals(120, summary.totalSeconds)
            assertEquals(1, summary.noteCount)
        }
    }

    @Test fun closingTheSummaryNeverLosesTheSession() = runAppTest {
        timerTest {
            sessions.saveTag(testTag("focus", "Focus"))
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.ToggleCategory(sessions.tags.value.first { it.id == "focus" }))
            advance(10_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession) // what swiping the sheet away does
            val saved = sessions.timenotes.value.single()
            assertEquals(listOf("Focus"), saved.tags.map { it.name }, "the tags chosen are kept, not dropped")
            assertEquals(10, saved.activeSeconds)
        }
    }

    @Test fun theBackupStaysUntilTheSessionIsSavedSoAKillDuringTheSummaryLosesNothing() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(10_000)
            vm.onAction(TimerAction.End)
            assertNotNull(settings.activeSessionBackupFlow.first(), "still there while the summary is open")
            vm.onAction(TimerAction.SaveSession)
            assertNull(settings.activeSessionBackupFlow.first())
        }
    }

    @Test fun aTitleChangedInTheSummaryIsSavedAndShownInTheEndEvent() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(10_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.UpdateSessionTitle("Renamed"))
            vm.onAction(TimerAction.SaveSession)
            val saved = sessions.timenotes.value.single()
            assertEquals("Renamed", saved.title)
            assertEquals("Session Ended: Renamed", saved.timelineEvents.first { it.type == EventType.END }.title)
        }
    }

    @Test fun keepGoingTakesTheEndBackAndCountsTheGapAsABreak() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(60_000)
            vm.onAction(TimerAction.End)
            advance(15_000) // the summary was open for 15 s
            vm.onAction(TimerAction.KeepGoing)

            assertNull(state.summary)
            assertTrue(state.isRunning)
            assertFalse(state.isPaused)
            assertTrue(state.timelineEvents.none { it.type == EventType.END }, "the end is gone from the timeline")
            assertEquals("Resumed (Break was 00:00:15)", state.timelineEvents.first().title)
            assertEquals(2, service.startCalls, "the service is started again")

            advance(30_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)
            val saved = sessions.timenotes.value.single()
            assertEquals(90, saved.activeSeconds, "60 s before and 30 s after")
            assertEquals(15, saved.pauseSeconds, "the gap is a break, not work")
        }
    }

    @Test fun keepGoingAfterEndingWhilePausedStaysPaused() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(60_000)
            vm.onAction(TimerAction.Pause)
            advance(10_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.KeepGoing)
            assertTrue(state.isRunning)
            assertTrue(state.isPaused)
            assertNotNull(settings.activeSessionBackupFlow.first())
        }
    }

    @Test fun discardingThrowsTheSessionAwayAndKeepsTheChips() = runAppTest {
        timerTest {
            sessions.saveTag(testTag("focus", "Focus"))
            vm.onAction(TimerAction.UpdateSessionTitle("Oops"))
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.ToggleCategory(sessions.tags.value.first { it.id == "focus" }))
            advance(5_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.DiscardSession)

            assertTrue(sessions.timenotes.value.isEmpty(), "nothing is saved")
            assertNull(state.summary)
            assertFalse(state.isRunning)
            assertEquals("", state.sessionTitle)
            assertEquals(listOf("focus"), state.selectedCategories.map { it.id }, "the chips stay for the next session")
            assertNull(settings.activeSessionBackupFlow.first(), "and a restart does not bring it back")
        }
    }

    @Test fun savingOrDiscardingWithoutASummaryDoesNothing() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.SaveSession)
            vm.onAction(TimerAction.DiscardSession)
            vm.onAction(TimerAction.KeepGoing)
            assertTrue(state.isRunning)
            assertTrue(sessions.timenotes.value.isEmpty())
        }
    }

    // --- editing the running session's timeline (F8) ---

    private fun com.oblutack.timenote.feature_timer.presentation.TimerViewModel.addNote(text: String, color: androidx.compose.ui.graphics.Color? = null) {
        onAction(TimerAction.OpenAddNoteDialog)
        onAction(TimerAction.UpdateDialogNoteText(text))
        if (color != null) onAction(TimerAction.UpdateDialogNoteColor(color))
        onAction(TimerAction.SaveNote)
    }

    @Test fun aNoteCanBeEditedWhileRunning() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(5_000)
            vm.addNote("teh idea")
            val note = state.timelineEvents.first { it.type == EventType.NOTE }

            vm.onAction(TimerAction.EditNote(note.id))
            assertTrue(state.isAddNoteDialogOpen)
            assertEquals(note.id, state.editingNoteId)
            assertEquals("teh idea", state.dialogNoteText, "the sheet opens on the note's own text, without the prefix")

            vm.onAction(TimerAction.UpdateDialogNoteText("the idea"))
            vm.onAction(TimerAction.UpdateDialogNoteColor(androidx.compose.ui.graphics.Color(0xFFE53935)))
            vm.onAction(TimerAction.SaveNote)

            val edited = state.timelineEvents.first { it.id == note.id }
            assertEquals("Note: the idea", edited.title)
            assertEquals(androidx.compose.ui.graphics.Color(0xFFE53935), edited.color)
            assertEquals(note.timestamp, edited.timestamp, "the time of the note does not change")
            assertEquals(state.timelineEvents.size, state.timelineEvents.map { it.id }.distinct().size)
            assertFalse(state.isAddNoteDialogOpen)
            assertNull(state.editingNoteId)

            val backup = Json { ignoreUnknownKeys = true }.decodeFromString<ActiveSessionBackup>(settings.activeSessionBackupFlow.first()!!)
            assertTrue(backup.timelineEvents.any { it.title == "Note: the idea" }, "the edit is in the backup too")
        }
    }

    @Test fun cancellingAnEditChangesNothingAndBringsBackTheDraft() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.addNote("original")
            val note = state.timelineEvents.first { it.type == EventType.NOTE }

            vm.onAction(TimerAction.OpenAddNoteDialog)
            vm.onAction(TimerAction.UpdateDialogNoteText("half-typed new note"))
            vm.onAction(TimerAction.DismissAddNoteDialog)

            vm.onAction(TimerAction.EditNote(note.id))
            vm.onAction(TimerAction.UpdateDialogNoteText("changed my mind"))
            vm.onAction(TimerAction.CloseAddNoteDialog)

            assertEquals("Note: original", state.timelineEvents.first { it.id == note.id }.title)
            assertNull(state.editingNoteId)
            assertEquals("half-typed new note", state.dialogNoteText, "the note that was being typed is back")
        }
    }

    @Test fun anEditWithNoTextIsIgnored() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.addNote("keep me")
            val note = state.timelineEvents.first { it.type == EventType.NOTE }
            vm.onAction(TimerAction.EditNote(note.id))
            vm.onAction(TimerAction.UpdateDialogNoteText("   "))
            vm.onAction(TimerAction.SaveNote)
            assertEquals("Note: keep me", state.timelineEvents.first { it.id == note.id }.title)
        }
    }

    @Test fun theEntriesFromTheClockCannotBeEditedOrDeleted() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(1_000)
            vm.onAction(TimerAction.Pause)
            val before = state.timelineEvents
            before.forEach { vm.onAction(TimerAction.EditNote(it.id)); vm.onAction(TimerAction.DeleteWaypoint(it.id)) }
            assertEquals(before, state.timelineEvents)
            assertFalse(state.isAddNoteDialogOpen)
        }
    }

    @Test fun deletingAWaypointRemovesItAndOffersUndo() = runAppTest {
        timerTest {
            val offered = mutableListOf<com.oblutack.timenote.feature_timer.domain.DeletedWaypoint>()
            vm.waypointDeleted.onEach { offered += it }.launchIn(backgroundScope)
            advance(0)

            vm.onAction(TimerAction.Start)
            vm.addNote("one")
            val note = state.timelineEvents.first { it.type == EventType.NOTE }
            vm.onAction(TimerAction.DeleteWaypoint(note.id))
            advance(0)

            assertTrue(state.timelineEvents.none { it.id == note.id })
            assertEquals(listOf(note.id), offered.map { it.event.id })
            assertTrue(state.timelineEvents.last().isLastItem, "the last entry still closes the line")
        }
    }

    @Test fun undoPutsTheWaypointBackWhereItWasEvenAfterNewerEntries() = runAppTest {
        timerTest {
            val offered = mutableListOf<com.oblutack.timenote.feature_timer.domain.DeletedWaypoint>()
            vm.waypointDeleted.onEach { offered += it }.launchIn(backgroundScope)
            advance(0)

            vm.onAction(TimerAction.Start)
            vm.addNote("first")
            vm.addNote("second")
            vm.addNote("third")
            val titlesBefore = state.timelineEvents.map { it.title }
            val second = state.timelineEvents.first { it.title == "Note: second" }

            vm.onAction(TimerAction.DeleteWaypoint(second.id))
            advance(0)
            vm.addNote("fourth") // a newer entry arrives while the Undo offer is up
            vm.onAction(TimerAction.RestoreWaypoint(offered.single()))

            assertEquals(
                listOf("Note: fourth") + titlesBefore,
                state.timelineEvents.map { it.title },
                "the entry is back between first and third, and the newer one stays on top"
            )
            vm.onAction(TimerAction.RestoreWaypoint(offered.single()))
            assertEquals(1, state.timelineEvents.count { it.id == second.id }, "restoring twice does not duplicate it")
        }
    }

    @Test fun editingAndDeletingWorkOnlyWhileTheSessionRuns() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.addNote("x")
            val note = state.timelineEvents.first { it.type == EventType.NOTE }
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)
            vm.onAction(TimerAction.EditNote(note.id))
            vm.onAction(TimerAction.DeleteWaypoint(note.id))
            assertFalse(state.isAddNoteDialogOpen)
            assertTrue(state.timelineEvents.any { it.id == note.id })
        }
    }

    @Test fun endingAnIdleTimerDoesNothing() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.End)
            assertNull(state.summary)
            assertTrue(sessions.timenotes.value.isEmpty())
        }
    }

    @Test fun notificationButtonsDrivePauseResumeAndEnd() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(10_000)

            commands.tryEmit(TimerServiceCommand.PAUSE)
            assertTrue(state.isPaused)
            commands.tryEmit(TimerServiceCommand.RESUME)
            assertFalse(state.isPaused)

            commands.tryEmit(TimerServiceCommand.END)
            assertFalse(state.isRunning)
            assertEquals(1, sessions.timenotes.value.size, "ending from the notification saves immediately")
        }
    }

    @Test fun notificationIsUpdatedOncePerSecondNotEveryTick() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(10_000)
            // the loop ticks 4x per second; without throttling this would be about 40
            assertTrue(service.notifications.size in 10..12, "was ${service.notifications.size}")
        }
    }

    @Test fun notesAreAddedToTheTimeline() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(20_000)
            vm.onAction(TimerAction.OpenAddNoteDialog)
            vm.onAction(TimerAction.UpdateDialogNoteText("Found the bug"))
            vm.onAction(TimerAction.SaveNote)

            assertFalse(state.isAddNoteDialogOpen)
            assertEquals("Note: Found the bug", state.timelineEvents.first().title)
            assertEquals("00:00:20", state.timelineEvents.first().timestamp)
        }
    }

    @Test fun blankNotesAreRejected() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.OpenAddNoteDialog)
            vm.onAction(TimerAction.UpdateDialogNoteText("   "))
            vm.onAction(TimerAction.SaveNote)
            assertEquals(1, state.timelineEvents.size)
        }
    }

    @Test fun voiceMemoIsAttachedToTheTimeline() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.StartVoiceMemo)
            assertTrue(state.isRecordingVoiceMemo)
            assertTrue(recorder.lastFileName!!.startsWith("VoiceMemo_"))

            vm.onAction(TimerAction.StopVoiceMemo)
            assertFalse(state.isRecordingVoiceMemo)
            assertEquals("${recorder.lastFileName}.m4a", state.timelineEvents.first().audioPath, "a file name is stored, not a path")
        }
    }

    @Test fun voiceMemoWithoutMicrophoneDoesNotStartAndWarns() = runAppTest {
        timerTest {
            recorder.canRecord = false
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.StartVoiceMemo)

            assertFalse(state.isRecordingVoiceMemo, "must not show a recording that is not happening")
            assertTrue(state.voiceMemoUnavailable)

            // once recording works again the warning goes away
            recorder.canRecord = true
            vm.onAction(TimerAction.StartVoiceMemo)
            assertTrue(state.isRecordingVoiceMemo)
            assertFalse(state.voiceMemoUnavailable)
        }
    }

    @Test fun branchedSessionRemembersItsParent() = runAppTest {
        timerTest {
            sessions.saveTimenote(
                com.oblutack.timenote.testutil.testNote("parent", title = "Parent session")
            )
            vm.onAction(TimerAction.SetParentLinks("parent", "wp1"))
            vm.onAction(TimerAction.Start)
            advance(5_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)

            val child = sessions.timenotes.value.first { it.id != "parent" }
            assertEquals("parent", child.parentTimenoteId)
            assertEquals("wp1", child.parentWaypointId)
        }
    }

    @Test fun branchBannerAndEventDoNotLeakIntoTheNextSession() = runAppTest {
        timerTest {
            sessions.saveTimenote(
                com.oblutack.timenote.testutil.testNote("parent", title = "Parent session")
                    .copy(timelineEvents = listOf(
                        com.oblutack.timenote.feature_timer.domain.TimelineEvent("wp1", "Waypoint", "00:00:05", EventType.NOTE)
                    ))
            )
            vm.onAction(TimerAction.SetParentLinks("parent", "wp1"))
            assertEquals("Parent session", state.parentSessionTitle)

            vm.onAction(TimerAction.Start)
            advance(5_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)

            // the branch is finished: no banner, and a fresh session must not claim to be a branch
            assertNull(state.parentSessionTitle)
            assertNull(state.parentWaypointTitle)

            vm.onAction(TimerAction.Start)
            assertEquals(listOf("Session Started"), state.timelineEvents.map { it.title })
        }
    }

    @Test fun reEnteringTheTimerScreenDoesNotReapplyAnOldBranch() = runAppTest {
        timerTest {
            sessions.saveTimenote(com.oblutack.timenote.testutil.testNote("parent", title = "Parent session"))

            // user taps "branch": the nav entry carries parentId/waypointId plus a unique token
            vm.onAction(TimerAction.SetParentLinks("parent", "wp1", "token-1"))
            vm.onAction(TimerAction.Start)
            advance(5_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)
            assertNull(state.parentTimenoteId)

            // the Timer tab is re-entered (saved state restores the same arguments): must be ignored
            vm.onAction(TimerAction.SetParentLinks("parent", "wp1", "token-1"))
            assertNull(state.parentTimenoteId, "an old branch must not be re-applied")
            assertNull(state.parentSessionTitle)

            // a genuinely new branch navigation carries a new token and does apply
            vm.onAction(TimerAction.SetParentLinks("parent", "wp1", "token-2"))
            assertEquals("parent", state.parentTimenoteId)
        }
    }

    @Test fun lastTimenoteLabelFollowsDeletions() = runAppTest {
        timerTest {
            // real sessions always have timeline events, which is what froze the old label
            val startEvent = com.oblutack.timenote.feature_timer.domain.TimelineEvent("e", "Session Started", "00:00:00", EventType.START)
            sessions.saveTimenote(
                com.oblutack.timenote.testutil.testNote("older", createdAt = 1_000).copy(title = "Older", duration = "00:01:00", timelineEvents = listOf(startEvent))
            )
            sessions.saveTimenote(
                com.oblutack.timenote.testutil.testNote("newer", createdAt = 2_000).copy(title = "Newer", duration = "00:02:00", timelineEvents = listOf(startEvent))
            )
            assertEquals("Newer", state.lastSessionTitle)

            sessions.deleteTimenote("newer")
            assertEquals("Older", state.lastSessionTitle)
            assertEquals("00:01:00", state.displayTime)

            sessions.deleteTimenote("older")
            assertEquals("", state.lastSessionTitle)
            assertEquals("00:00:00", state.displayTime)
            assertTrue(state.timelineEvents.isEmpty())
        }
    }

    @Test fun deletingTheJustFinishedSessionClearsTheLabel() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            advance(5_000)
            vm.onAction(TimerAction.End)
            vm.onAction(TimerAction.SaveSession)
            assertEquals("Untitled Session", state.lastSessionTitle)

            sessions.deleteTimenote(sessions.timenotes.value.single().id)
            assertEquals("", state.lastSessionTitle)
            assertTrue(state.timelineEvents.isEmpty())
        }
    }

    // --- restoring a session after the app was killed ---

    private fun backup(
        folderId: String? = null,
        categoryIds: List<String> = emptyList(),
        paused: Boolean = false
    ) = Json { encodeDefaults = true }.encodeToString(
        ActiveSessionBackup(
            sessionTitle = "Deep work",
            startTimeMillis = BASE - 30_000,
            totalPauseMillis = 0,
            lastPauseStartTimeMillis = if (paused) BASE - 10_000 else null,
            isPaused = paused,
            timelineEvents = emptyList(),
            selectedFolderId = folderId,
            selectedCategoryIds = categoryIds
        )
    )

    @Test fun restoredSessionKeepsRunningFromTheOriginalStart() = runAppTest {
        timerTest {
            settings.saveActiveSession(backup())
            assertTrue(state.isRunning)
            assertEquals("Deep work", state.sessionTitle)
            assertEquals(1, service.startCalls, "the foreground service is restarted")

            advance(1_000)
            assertEquals("00:00:31", state.displayTime)
        }
    }

    @Test fun restoredPausedSessionStaysPaused() = runAppTest {
        timerTest {
            settings.saveActiveSession(backup(paused = true))
            assertTrue(state.isPaused)
            advance(5_000)
            assertEquals("00:00:20", state.displayTime, "frozen at the moment it was paused")
        }
    }

    @Test fun restoredSessionBringsBackFolderAndTags() = runAppTest {
        timerTest {
            sessions.saveFolder(testFolder("writing"))
            sessions.saveTag(testTag("focus"))
            settings.saveActiveSession(backup(folderId = "writing", categoryIds = listOf("focus")))

            assertEquals("writing", state.selectedFolder?.id)
            assertEquals(listOf("focus"), state.selectedCategories.map { it.id })
        }
    }

    @Test fun restoredFolderAndTagsAreAppliedWhenTheyLoadLater() = runAppTest {
        timerTest {
            settings.saveActiveSession(backup(folderId = "late-folder", categoryIds = listOf("late-tag")))
            assertNull(state.selectedFolder)

            sessions.saveFolder(testFolder("late-folder"))
            sessions.saveTag(testTag("late-tag"))

            assertEquals("late-folder", state.selectedFolder?.id)
            assertEquals(listOf("late-tag"), state.selectedCategories.map { it.id })
        }
    }

    @Test fun corruptedBackupIsDiscardedInsteadOfCrashing() = runAppTest {
        timerTest {
            settings.saveActiveSession("this is not json")
            assertFalse(state.isRunning)
            assertNull(settings.activeSessionBackupFlow.first())
        }
    }

    // --- starting from a folder (B7) ---

    @Test fun startSessionInAFolderSelectsThatFolder() = runAppTest {
        timerTest {
            sessions.saveFolder(testFolder("thesis"))
            vm.onAction(TimerAction.PrepareInFolder("thesis", "t1"))
            assertEquals("thesis", state.selectedFolder?.id)
        }
    }

    @Test fun theFolderIsOnlyAppliedOncePerNavigation() = runAppTest {
        timerTest {
            sessions.saveFolder(testFolder("thesis"))
            vm.onAction(TimerAction.PrepareInFolder("thesis", "t1"))
            vm.onAction(TimerAction.SelectFolder(sessions.folders.value.single())) // the user unselects it
            assertNull(state.selectedFolder)

            vm.onAction(TimerAction.PrepareInFolder("thesis", "t1")) // the same navigation delivered again (rotation, tab)
            assertNull(state.selectedFolder, "an old navigation must not undo the user's choice")

            vm.onAction(TimerAction.PrepareInFolder("thesis", "t2")) // a new tap on "Start Session"
            assertEquals("thesis", state.selectedFolder?.id)
        }
    }

    @Test fun aRunningSessionKeepsItsFolder() = runAppTest {
        timerTest {
            sessions.saveFolder(testFolder("thesis")); sessions.saveFolder(testFolder("gym"))
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.SelectFolder(sessions.folders.value.first { it.id == "gym" }))
            vm.onAction(TimerAction.PrepareInFolder("thesis", "t1"))
            assertEquals("gym", state.selectedFolder?.id)
        }
    }

    @Test fun aFolderThatLoadsLaterIsStillSelected() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.PrepareInFolder("late", "t1"))
            assertNull(state.selectedFolder)
            sessions.saveFolder(testFolder("late"))
            assertEquals("late", state.selectedFolder?.id)
        }
    }

    // --- the quick note sheet keeps what was typed (B1) ---

    @Test fun closingTheNoteSheetWithoutChoosingKeepsTheDraft() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.OpenAddNoteDialog)
            vm.onAction(TimerAction.UpdateDialogNoteText("half a thought"))

            vm.onAction(TimerAction.DismissAddNoteDialog) // swipe down, tap outside, Back

            assertFalse(state.isAddNoteDialogOpen)
            assertEquals("half a thought", state.dialogNoteText)
            vm.onAction(TimerAction.OpenAddNoteDialog)
            assertEquals("half a thought", state.dialogNoteText, "it is there when the sheet opens again")
        }
    }

    @Test fun cancellingTheNoteSheetThrowsTheDraftAway() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.OpenAddNoteDialog)
            vm.onAction(TimerAction.UpdateDialogNoteText("never mind"))
            vm.onAction(TimerAction.CloseAddNoteDialog)
            assertEquals("", state.dialogNoteText)
        }
    }
}
