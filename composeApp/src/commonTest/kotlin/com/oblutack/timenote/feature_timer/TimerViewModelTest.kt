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

            assertTrue(state.isCategoryPopupOpen, "no tags selected, so the save popup is shown")
            vm.onAction(TimerAction.SkipCategoriesAndSave)

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
            vm.onAction(TimerAction.SkipCategoriesAndSave)
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

    @Test fun endingWithTagsSelectedSavesWithoutAsking() = runAppTest {
        timerTest {
            sessions.saveTag(testTag("focus", "Focus"))
            vm.onAction(TimerAction.Start)
            vm.onAction(TimerAction.ToggleCategory(sessions.tags.value.first { it.id == "focus" }))
            advance(10_000)
            vm.onAction(TimerAction.End)

            assertFalse(state.isCategoryPopupOpen)
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
            vm.onAction(TimerAction.SkipCategoriesAndSave)
            assertEquals("writing", sessions.timenotes.value.single().folderId)
        }
    }

    @Test fun endingAnIdleTimerDoesNothing() = runAppTest {
        timerTest {
            vm.onAction(TimerAction.End)
            assertFalse(state.isCategoryPopupOpen)
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
            vm.onAction(TimerAction.SkipCategoriesAndSave)

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
            vm.onAction(TimerAction.SkipCategoriesAndSave)

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
            vm.onAction(TimerAction.SkipCategoriesAndSave)
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
            vm.onAction(TimerAction.SkipCategoriesAndSave)
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
}
