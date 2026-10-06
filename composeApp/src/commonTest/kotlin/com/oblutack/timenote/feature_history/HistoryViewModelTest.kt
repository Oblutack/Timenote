package com.oblutack.timenote.feature_history

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.presentation.HistoryViewModel
import com.oblutack.timenote.feature_history.presentation.TrashViewModel
import com.oblutack.timenote.testutil.FakeAudioPlayer
import com.oblutack.timenote.testutil.FakeAudioRecorder
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NOW = 1_700_000_100_000L   // 2023-11-14 22:15 UTC
private const val DAY = 86_400_000L

private fun dayKey(millis: Long) =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private class HistoryEnv(scope: TestScope) {
    val sessions = SessionRepository(FakeTimenoteDao(), scope.backgroundScope, now = { NOW })
    val player = FakeAudioPlayer()
    val recorder = FakeAudioRecorder()
    val vm = HistoryViewModel(sessions, player, recorder, now = { NOW })
}

class HistoryViewModelTest {

    // --- deleting branches ---

    @Test fun deletingANoteWithoutChildrenTrashesItImmediately() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a"))
        env.vm.requestDelete(env.sessions.getTimenoteById("a")!!)
        assertNull(env.vm.sessionPendingDelete.value)
        assertEquals(listOf("a"), env.sessions.deletedTimenotes.value.map { it.id })
    }

    @Test fun deletingANoteWithChildrenAsksFirst() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("parent"))
        env.sessions.saveTimenote(testNote("child", "parent"))
        env.sessions.saveTimenote(testNote("grandchild", "child"))

        env.vm.requestDelete(env.sessions.getTimenoteById("parent")!!)

        assertEquals("parent", env.vm.sessionPendingDelete.value?.id)
        assertEquals(2, env.vm.descendantCount.value)
        assertTrue(env.sessions.deletedTimenotes.value.isEmpty(), "nothing is deleted until confirmed")
    }

    @Test fun confirmingCascadeTrashesTheWholeBranch() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("parent"))
        env.sessions.saveTimenote(testNote("child", "parent"))
        env.vm.requestDelete(env.sessions.getTimenoteById("parent")!!)

        env.vm.confirmDelete(cascade = true)

        assertEquals(setOf("parent", "child"), env.sessions.deletedTimenotes.value.map { it.id }.toSet())
        assertNull(env.vm.sessionPendingDelete.value)
        assertEquals(0, env.vm.descendantCount.value)
    }

    @Test fun confirmingWithoutCascadeKeepsChildrenAsRoots() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("parent"))
        env.sessions.saveTimenote(testNote("child", "parent"))
        env.vm.requestDelete(env.sessions.getTimenoteById("parent")!!)

        env.vm.confirmDelete(cascade = false)

        assertEquals(listOf("parent"), env.sessions.deletedTimenotes.value.map { it.id })
        assertNull(env.sessions.getTimenoteById("child")?.parentTimenoteId)
    }

    @Test fun cancellingDeleteLeavesEverythingAlone() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("parent"))
        env.sessions.saveTimenote(testNote("child", "parent"))
        env.vm.requestDelete(env.sessions.getTimenoteById("parent")!!)
        env.vm.cancelDelete()
        assertNull(env.vm.sessionPendingDelete.value)
        assertTrue(env.sessions.deletedTimenotes.value.isEmpty())
    }

    // --- statistics ---

    @Test fun familyTimeSumsTheNodeAndAllDescendants() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("root", activeSeconds = 3_600))
        env.sessions.saveTimenote(testNote("child", "root", activeSeconds = 1_800))
        env.sessions.saveTimenote(testNote("grandchild", "child", activeSeconds = 60))
        env.sessions.saveTimenote(testNote("other", activeSeconds = 9_999))

        assertEquals("01:31:00", env.vm.calculateFamilyTime("root"))
        assertEquals("00:31:00", env.vm.calculateFamilyTime("child"))
    }

    @Test fun heatmapTotalsActiveSecondsPerDay() = runAppTest {
        val env = HistoryEnv(this)
        backgroundScope.launch { env.vm.heatmapData.collect {} }
        env.sessions.saveTimenote(testNote("a", createdAt = NOW, activeSeconds = 600))
        env.sessions.saveTimenote(testNote("b", createdAt = NOW + 60_000, activeSeconds = 300))
        env.sessions.saveTimenote(testNote("c", createdAt = NOW - 3 * DAY, activeSeconds = 120))

        val heat = env.vm.heatmapData.value
        assertEquals(900, heat[dayKey(NOW)])
        assertEquals(120, heat[dayKey(NOW - 3 * DAY)])
    }

    @Test fun streaksCountConsecutiveDaysEndingToday() = runAppTest {
        val env = HistoryEnv(this)
        backgroundScope.launch { env.vm.streaks.collect {} }
        listOf(0, 1, 2).forEach { daysAgo ->
            env.sessions.saveTimenote(testNote("d$daysAgo", createdAt = NOW - daysAgo * DAY, activeSeconds = 600))
        }
        assertEquals(Pair(3, 3), env.vm.streaks.value)
    }

    @Test fun dailySummaryShowsTotalCountAndTopTag() = runAppTest {
        val env = HistoryEnv(this)
        val focus = testTag("focus", "Focus")
        val admin = testTag("admin", "Admin")
        env.sessions.saveTimenote(testNote("a", createdAt = NOW, activeSeconds = 600).copy(tags = listOf(focus)))
        env.sessions.saveTimenote(testNote("b", createdAt = NOW + 1_000, activeSeconds = 300).copy(tags = listOf(focus, admin)))

        val date = Instant.fromEpochMilliseconds(NOW).toLocalDateTime(TimeZone.currentSystemDefault()).date
        env.vm.selectDateForSummary(date)

        val summary = env.vm.selectedDailySummary.value!!
        assertEquals(900, summary.totalSeconds)
        assertEquals(2, summary.sessionCount)
        assertEquals("Focus", summary.topTag?.name)

        env.vm.closeDailySummary()
        assertNull(env.vm.selectedDailySummary.value)
    }

    @Test fun dailySummaryForAnEmptyDayIsZero() = runAppTest {
        val env = HistoryEnv(this)
        val date = Instant.fromEpochMilliseconds(NOW - 10 * DAY).toLocalDateTime(TimeZone.currentSystemDefault()).date
        env.vm.selectDateForSummary(date)
        val summary = env.vm.selectedDailySummary.value!!
        assertEquals(0, summary.sessionCount)
        assertNull(summary.topTag)
    }

    // --- filters ---

    @Test fun tagFiltersToggleAndClear() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.toggleFilterTag("a"); env.vm.toggleFilterTag("b")
        assertEquals(setOf("a", "b"), env.vm.selectedFilterTags.value)
        env.vm.toggleFilterTag("a")
        assertEquals(setOf("b"), env.vm.selectedFilterTags.value)
        env.vm.clearTagFilters()
        assertTrue(env.vm.selectedFilterTags.value.isEmpty())
    }

    // --- folders ---

    @Test fun newFolderGetsATimestampIdAndEditKeepsCreationDate() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.saveFolder(name = "Writing", description = "Novel", color = Color.Red)
        val created = env.sessions.folders.value.single()
        assertEquals(NOW.toString(), created.id)
        assertEquals("Novel", created.description)

        env.vm.saveFolder(id = created.id, name = "Writing 2", color = Color.Blue)
        val edited = env.sessions.folders.value.single()
        assertEquals("Writing 2", edited.name)
        assertEquals(created.createdAt, edited.createdAt)
    }

    // --- audio ---

    @Test fun tappingAnAudioClipPlaysAndTappingAgainPauses() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.playAudio("/m/1.m4a")
        assertEquals("/m/1.m4a", env.vm.playingAudioPath.value)
        assertTrue(env.player.playing)

        env.vm.playAudio("/m/1.m4a")
        assertNull(env.vm.playingAudioPath.value)
        assertTrue(!env.player.playing)
    }

    @Test fun finishedPlaybackResetsTheButton() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.playAudio("/m/1.m4a")
        env.player.finishPlayback()
        assertNull(env.vm.playingAudioPath.value)
    }

    @Test fun recordingForATimenoteAttachesTheVoiceNote() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a"))
        env.vm.startRecordingForTimenote("a")
        assertEquals("a", env.vm.recordingTimenoteId.value)
        env.vm.stopRecordingForTimenote()
        assertNull(env.vm.recordingTimenoteId.value)
        assertEquals(listOf("/fake/SessionMemo_a.m4a"), env.sessions.getTimenoteById("a")?.voiceNotes)

        env.vm.deleteVoiceNote("a", "/fake/SessionMemo_a.m4a")
        assertTrue(env.sessions.getTimenoteById("a")!!.voiceNotes.isEmpty())
    }

    // --- trash ---

    @Test fun trashViewModelRestoresAndEmpties() = runAppTest {
        val sessions = SessionRepository(FakeTimenoteDao(), backgroundScope, now = { NOW })
        val trash = TrashViewModel(sessions)
        sessions.saveTimenote(testNote("a")); sessions.saveTimenote(testNote("b"))
        sessions.saveFolder(testFolder("f"))
        sessions.deleteTimenote("a"); sessions.deleteTimenote("b"); sessions.deleteFolder("f")

        assertEquals(setOf("a", "b"), trash.deletedTimenotes.value.map { it.id }.toSet())
        trash.restoreTimenote("a")
        assertEquals(listOf("a"), sessions.timenotes.value.map { it.id })

        trash.emptyTrash()
        assertTrue(trash.deletedTimenotes.value.isEmpty())
        assertTrue(trash.deletedFolders.value.isEmpty())
    }
}
