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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.oblutack.timenote.core.DirectoryAudioFiles
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId

private const val NOW = 1_700_000_100_000L   // 2023-11-14 22:15 UTC
private const val DAY = 86_400_000L

private fun dayKey(millis: Long) =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private class HistoryEnv(scope: TestScope) {
    val sessions = SessionRepository(FakeTimenoteDao(), scope.backgroundScope, FakeDefaultTagsState(), FakeDeviceId(), now = { NOW })
    val player = FakeAudioPlayer()
    val recorder = FakeAudioRecorder()
    val audioFiles = DirectoryAudioFiles("/fake")
    val vm = HistoryViewModel(sessions, player, recorder, audioFiles, now = { NOW })
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

    @Test fun newFolderGetsAUniqueIdAndEditKeepsCreationDate() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.saveFolder(name = "Writing", description = "Novel", color = Color.Red)
        val created = env.sessions.folders.value.single()
        assertEquals(36, created.id.length, "new folders get a UUID")
        assertEquals("Novel", created.description)

        env.vm.saveFolder(id = created.id, name = "Writing 2", color = Color.Blue)
        val edited = env.sessions.folders.value.single()
        assertEquals("Writing 2", edited.name)
        assertEquals(created.createdAt, edited.createdAt)
    }

    @Test fun editingAFolderDoesNotUnpinItOrTouchOtherFields() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.saveFolder(name = "Writing", color = Color.Red)
        val id = env.sessions.folders.value.single().id
        env.vm.toggleFolderPin(id)
        assertTrue(env.sessions.getFolderById(id)!!.isPinned)

        env.vm.saveFolder(id = id, name = "Writing 2", description = "Novel", color = Color.Blue)

        val edited = env.sessions.getFolderById(id)!!
        assertEquals("Writing 2", edited.name)
        assertEquals("Novel", edited.description)
        assertEquals(Color.Blue, edited.color)
        assertTrue(edited.isPinned, "a rename must not unpin the folder")
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

    @Test fun playbackResolvesAStoredNameToTheLocalFolder() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.playAudio("SessionMemo_1.m4a")
        assertEquals("/fake/SessionMemo_1.m4a", env.player.lastPlayed)
        assertEquals("SessionMemo_1.m4a", env.vm.playingAudioPath.value, "the UI keeps working with the stored reference")
    }

    @Test fun playbackStillWorksForLegacyAbsolutePaths() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.playAudio("/data/user/0/old/app/files/voice_memos/legacy.m4a")
        assertEquals("/fake/legacy.m4a", env.player.lastPlayed)
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
        assertEquals(listOf("SessionMemo_a.m4a"), env.sessions.getTimenoteById("a")?.voiceNotes)

        env.vm.deleteVoiceNote("a", "SessionMemo_a.m4a")
        assertTrue(env.sessions.getTimenoteById("a")!!.voiceNotes.isEmpty())
    }

    @Test fun recordingForATimenoteWithoutMicrophoneDoesNotStart() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a"))
        env.recorder.canRecord = false

        env.vm.startRecordingForTimenote("a")

        assertNull(env.vm.recordingTimenoteId.value)
        assertTrue(env.vm.micUnavailable.value)
        assertTrue(env.sessions.getTimenoteById("a")!!.voiceNotes.isEmpty())
    }

    // --- trash ---

    @Test fun trashViewModelRestoresAndEmpties() = runAppTest {
        val sessions = SessionRepository(FakeTimenoteDao(), backgroundScope, FakeDefaultTagsState(), FakeDeviceId(), now = { NOW })
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

    // --- the filter bar (F17) ---

    @Test fun theFilterChipsSwitchTheirFiltersOnAndOff() = runAppTest {
        val env = HistoryEnv(this)
        val vm = env.vm
        assertTrue(!vm.filter.value.isActive)

        vm.toggleHasNotes(); vm.toggleHasVoice(); vm.toggleHasBranches(); vm.togglePinnedOnly()
        vm.setFolderFilter("f1")
        vm.setDateFilter(com.oblutack.timenote.feature_history.domain.DateFilter.Last7Days)
        val on = vm.filter.value
        assertTrue(on.hasNotes && on.hasVoice && on.hasBranches && on.pinnedOnly)
        assertEquals("f1", on.folderId)
        assertEquals(com.oblutack.timenote.feature_history.domain.DateFilter.Last7Days, on.date)

        vm.toggleHasNotes()
        assertTrue(!vm.filter.value.hasNotes)
        vm.setFolderFilter(null)
        assertNull(vm.filter.value.folderId)
    }

    @Test fun clearAllTakesEveryFilterTheTagsAndTheSearchWordsOff() = runAppTest {
        val env = HistoryEnv(this)
        val vm = env.vm
        vm.toggleHasVoice()
        vm.setDateFilter(com.oblutack.timenote.feature_history.domain.DateFilter.Today)
        vm.toggleFilterTag("t1")
        vm.updateSearchQuery("review")

        vm.clearAllFilters()

        assertTrue(!vm.filter.value.isActive)
        assertTrue(vm.selectedFilterTags.value.isEmpty())
        assertEquals("", vm.searchQuery.value)
    }

    // --- duplicating a session (F23) ---

    @Test fun duplicatingCopiesTheFrameButNotTheTimeOrTheTimeline() = runAppTest {
        val env = HistoryEnv(this)
        val source = testNote("a", title = "Weekly review", activeSeconds = 3_000, pauseSeconds = 600).copy(
            description = "Template text",
            folderId = "f1",
            tags = listOf(testTag("t1", "Admin")),
            duration = "01:00:00",
            isPinned = true,
            parentTimenoteId = "p",
            parentWaypointId = "w",
            voiceNotes = listOf("memo.m4a"),
            timelineEvents = listOf(com.oblutack.timenote.feature_timer.domain.TimelineEvent("e1", "Note: x", "00:01:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE))
        )
        env.sessions.saveTimenote(source)

        val copyId = env.vm.duplicateTimenote("a")!!
        val copy = env.sessions.getTimenoteById(copyId)!!

        assertTrue(copyId != "a")
        assertEquals("Weekly review (copy)", copy.title)
        assertEquals("Template text", copy.description)
        assertEquals("f1", copy.folderId)
        assertEquals(listOf("Admin"), copy.tags.map { it.name })
        assertEquals(0, copy.activeSeconds)
        assertEquals(0, copy.pauseSeconds)
        assertEquals("00:00:00", copy.duration)
        assertEquals(NOW, copy.createdAt)
        assertTrue(copy.timelineEvents.isEmpty())
        assertTrue(copy.voiceNotes.isEmpty())
        assertNull(copy.parentTimenoteId)
        assertEquals(false, copy.isPinned)
        assertEquals("Weekly review", env.sessions.getTimenoteById("a")!!.title, "the original is untouched")
    }

    @Test fun duplicatingASessionThatIsGoneDoesNothing() = runAppTest {
        val env = HistoryEnv(this)
        assertNull(env.vm.duplicateTimenote("missing"))
        assertTrue(env.sessions.timenotes.value.isEmpty())
    }

    @Test fun anUndoOfferReachesWhoeverListensEvenFromAnotherScreensViewModel() = runAppTest {
        // The details screen and the list each have their own view model; the app listens to the repository
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a", title = "From details"))
        val offers = mutableListOf<com.oblutack.timenote.data.repository.UndoDelete>()
        backgroundScope.launch { env.sessions.undoOffers.collect { offers += it } }
        val otherScreensViewModel = HistoryViewModel(env.sessions, env.player, env.recorder, env.audioFiles, now = { NOW })

        otherScreensViewModel.requestDelete(env.sessions.getTimenoteById("a")!!)

        assertEquals(listOf("a"), offers.single().ids)
    }

    // --- Undo after a delete (B4) ---

    @Test fun deletingANoteOffersUndoAndUndoBringsItBack() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a", title = "Morning pages"))
        val offers = mutableListOf<com.oblutack.timenote.data.repository.UndoDelete>()
        backgroundScope.launch { env.vm.undoDelete.collect { offers += it } }

        env.vm.requestDelete(env.sessions.getTimenoteById("a")!!)

        assertEquals(1, offers.size)
        assertEquals(listOf("a"), offers.single().ids)
        assertTrue("Morning pages" in offers.single().message)
        assertTrue(env.sessions.timenotes.value.isEmpty())

        env.vm.restoreTimenotes(offers.single().ids)

        assertEquals(listOf("a"), env.sessions.timenotes.value.map { it.id })
        assertTrue(env.sessions.deletedTimenotes.value.isEmpty())
    }

    @Test fun deletingAWholeBranchOffersUndoForAllOfIt() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("parent"))
        env.sessions.saveTimenote(testNote("child", "parent"))
        env.sessions.saveTimenote(testNote("grandchild", "child"))
        val offers = mutableListOf<com.oblutack.timenote.data.repository.UndoDelete>()
        backgroundScope.launch { env.vm.undoDelete.collect { offers += it } }

        env.vm.requestDelete(env.sessions.getTimenoteById("parent")!!)
        env.vm.confirmDelete(cascade = true)
        assertTrue(env.sessions.timenotes.value.isEmpty())

        assertEquals(setOf("parent", "child", "grandchild"), offers.single().ids.toSet())
        env.vm.restoreTimenotes(offers.single().ids)
        assertEquals(setOf("parent", "child", "grandchild"), env.sessions.timenotes.value.map { it.id }.toSet())
    }

    @Test fun cancellingTheBranchQuestionOffersNothing() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("parent"))
        env.sessions.saveTimenote(testNote("child", "parent"))
        val offers = mutableListOf<com.oblutack.timenote.data.repository.UndoDelete>()
        backgroundScope.launch { env.vm.undoDelete.collect { offers += it } }

        env.vm.requestDelete(env.sessions.getTimenoteById("parent")!!)
        env.vm.cancelDelete()

        assertTrue(offers.isEmpty())
        assertEquals(2, env.sessions.timenotes.value.size)
    }

    @Test fun oneSwipeIsOneDeleteEvenIfItIsReportedTwice() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a"))
        env.sessions.saveTimenote(testNote("b"))
        val offers = mutableListOf<com.oblutack.timenote.data.repository.UndoDelete>()
        backgroundScope.launch { env.vm.undoDelete.collect { offers += it } }

        val a = env.sessions.getTimenoteById("a")!!
        env.vm.requestDelete(a)
        env.vm.requestDelete(a) // the swipe widget reports the same delete again

        assertEquals(1, offers.size, "one Undo offer, not two queued one after the other")

        env.vm.requestDelete(env.sessions.getTimenoteById("b")!!) // a different session is a different delete
        assertEquals(2, offers.size)
    }

    // --- Selecting several sessions (F19) ---

    private fun offersOf(env: HistoryEnv, scope: TestScope): MutableList<com.oblutack.timenote.data.repository.UndoDelete> {
        val offers = mutableListOf<com.oblutack.timenote.data.repository.UndoDelete>()
        scope.backgroundScope.launch { env.vm.undoDelete.collect { offers += it } }
        return offers
    }

    @Test fun tappingSelectsAndDeselectsAndClearEmptiesTheSelection() = runAppTest {
        val env = HistoryEnv(this)
        env.vm.toggleSelected("a")
        env.vm.toggleSelected("b")
        assertEquals(setOf("a", "b"), env.vm.selectedIds.value)
        env.vm.toggleSelected("a")
        assertEquals(setOf("b"), env.vm.selectedIds.value)
        env.vm.selectAll(listOf("x", "y"))
        assertEquals(setOf("x", "y"), env.vm.selectedIds.value)
        env.vm.clearSelection()
        assertTrue(env.vm.selectedIds.value.isEmpty())
    }

    @Test fun deletingSeveralSelectedIsOneTrashAndOneUndo() = runAppTest {
        val env = HistoryEnv(this)
        listOf("a", "b", "c").forEach { env.sessions.saveTimenote(testNote(it)) }
        val offers = offersOf(env, this)
        env.vm.selectAll(listOf("a", "b"))

        env.vm.requestDeleteSelected()

        assertEquals(listOf("c"), env.sessions.timenotes.value.map { it.id })
        assertEquals(1, offers.size)
        assertEquals("2 sessions moved to trash", offers.single().message)
        assertEquals(setOf("a", "b"), offers.single().ids.toSet())
        assertTrue(env.vm.selectedIds.value.isEmpty(), "the selection ends after the action")
        env.vm.restoreTimenotes(offers.single().ids)
        assertEquals(setOf("a", "b", "c"), env.sessions.timenotes.value.map { it.id }.toSet())
    }

    @Test fun selectingAWholeBranchDeletesItWithoutAskingAnything() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("p"))
        env.sessions.saveTimenote(testNote("c", "p"))
        env.vm.selectAll(listOf("p", "c"))

        env.vm.requestDeleteSelected()

        assertNull(env.vm.bulkPendingDelete.value)
        assertTrue(env.sessions.timenotes.value.isEmpty())
    }

    @Test fun branchesLeftBehindAreAskedAboutOnceForAllSelected() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("p1"))
        env.sessions.saveTimenote(testNote("c1", "p1"))
        env.sessions.saveTimenote(testNote("p2"))
        env.sessions.saveTimenote(testNote("c2", "p2"))
        env.sessions.saveTimenote(testNote("c2b", "c2"))
        env.vm.selectAll(listOf("p1", "p2"))

        env.vm.requestDeleteSelected()

        val pending = env.vm.bulkPendingDelete.value!!
        assertEquals(setOf("p1", "p2"), pending.ids)
        assertEquals(3, pending.branchCount)
        assertEquals(5, env.sessions.timenotes.value.size, "nothing is deleted until the answer")
    }

    @Test fun answeringDeleteAllTrashesTheBranchesToo() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("p"))
        env.sessions.saveTimenote(testNote("c", "p"))
        env.sessions.saveTimenote(testNote("keep"))
        val offers = offersOf(env, this)
        env.vm.selectAll(listOf("p"))
        env.vm.requestDeleteSelected()

        env.vm.confirmDeleteSelected(cascade = true)

        assertEquals(listOf("keep"), env.sessions.timenotes.value.map { it.id })
        assertEquals("2 sessions moved to trash", offers.single().message)
        assertEquals(setOf("p", "c"), offers.single().ids.toSet())
        assertNull(env.vm.bulkPendingDelete.value)
        assertTrue(env.vm.selectedIds.value.isEmpty())
    }

    @Test fun answeringDeleteOnlyTheSelectedKeepsTheBranchesAsRoots() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("p"))
        env.sessions.saveTimenote(testNote("c", "p"))
        env.sessions.saveTimenote(testNote("g", "c"))
        env.vm.selectAll(listOf("p"))
        env.vm.requestDeleteSelected()

        env.vm.confirmDeleteSelected(cascade = false)

        assertEquals(setOf("p"), env.sessions.deletedTimenotes.value.map { it.id }.toSet())
        assertNull(env.sessions.getTimenoteById("c")?.parentTimenoteId, "the direct branch becomes a root")
        assertEquals("c", env.sessions.getTimenoteById("g")?.parentTimenoteId, "its own branch stays attached")
    }

    @Test fun keepingBranchesDoesNotDetachABranchThatIsDeletedToo() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("p"))
        env.sessions.saveTimenote(testNote("c", "p"))
        env.sessions.saveTimenote(testNote("g", "c"))
        val offers = offersOf(env, this)
        env.vm.selectAll(listOf("p", "c"))
        env.vm.requestDeleteSelected()
        env.vm.confirmDeleteSelected(cascade = false)

        // c was deleted along with p, so its own link to p must survive; only g (left behind) becomes a root
        assertEquals("p", env.sessions.deletedTimenotes.value.single { it.id == "c" }.parentTimenoteId)
        assertNull(env.sessions.getTimenoteById("g")?.parentTimenoteId)
        assertTrue(offers.isEmpty(), "branches were detached, so no Undo is offered (as for one session)")
    }

    @Test fun cancellingTheBulkQuestionDeletesNothingAndKeepsTheSelection() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("p"))
        env.sessions.saveTimenote(testNote("c", "p"))
        env.vm.selectAll(listOf("p"))
        env.vm.requestDeleteSelected()

        env.vm.cancelDeleteSelected()

        assertNull(env.vm.bulkPendingDelete.value)
        assertEquals(2, env.sessions.timenotes.value.size)
        assertEquals(setOf("p"), env.vm.selectedIds.value)
    }

    @Test fun aSelectedSessionThatIsGoneIsIgnored() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a"))
        val offers = offersOf(env, this)
        env.vm.selectAll(listOf("a", "ghost"))

        env.vm.requestDeleteSelected()

        assertEquals(listOf("a"), offers.single().ids)
        assertEquals("\"a\" moved to trash", offers.single().message)
    }

    @Test fun pinningTheSelectionPinsAllOrUnpinsAllWhenAllArePinned() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a"))
        env.sessions.saveTimenote(testNote("b").copy(isPinned = true))
        env.vm.selectAll(listOf("a", "b"))
        env.vm.pinSelected()
        assertTrue(env.sessions.getTimenoteById("a")!!.isPinned && env.sessions.getTimenoteById("b")!!.isPinned)
        assertTrue(env.vm.selectedIds.value.isEmpty())

        env.vm.selectAll(listOf("a", "b"))
        env.vm.pinSelected()
        assertFalse(env.sessions.getTimenoteById("a")!!.isPinned || env.sessions.getTimenoteById("b")!!.isPinned)
    }

    @Test fun swipingRightTogglesThePinOfThatSession() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveTimenote(testNote("a"))
        env.vm.togglePinOf(env.sessions.getTimenoteById("a")!!)
        assertTrue(env.sessions.getTimenoteById("a")!!.isPinned)
        env.vm.togglePinOf(env.sessions.getTimenoteById("a")!!)
        assertFalse(env.sessions.getTimenoteById("a")!!.isPinned)
    }

    @Test fun movingTheSelectionToAFolderAndOutOfIt() = runAppTest {
        val env = HistoryEnv(this)
        env.sessions.saveFolder(testFolder("f"))
        env.sessions.saveTimenote(testNote("a"))
        env.sessions.saveTimenote(testNote("b"))
        env.sessions.saveTimenote(testNote("c"))
        env.vm.selectAll(listOf("a", "b"))
        env.vm.moveSelectedToFolder("f")
        assertEquals("f", env.sessions.getTimenoteById("a")!!.folderId)
        assertEquals("f", env.sessions.getTimenoteById("b")!!.folderId)
        assertNull(env.sessions.getTimenoteById("c")!!.folderId)
        assertTrue(env.vm.selectedIds.value.isEmpty())

        env.vm.selectAll(listOf("a"))
        env.vm.moveSelectedToFolder(null)
        assertNull(env.sessions.getTimenoteById("a")!!.folderId)
        assertEquals("f", env.sessions.getTimenoteById("b")!!.folderId)
    }

    @Test fun taggingTheSelectionAddsThenRemovesAndKeepsTheSelection() = runAppTest {
        val env = HistoryEnv(this)
        val red = testTag("red")
        val blue = testTag("blue")
        env.sessions.saveTimenote(testNote("a").copy(tags = listOf(blue)))
        env.sessions.saveTimenote(testNote("b"))
        env.vm.selectAll(listOf("a", "b"))

        env.vm.toggleTagOnSelected(red)
        assertEquals(listOf("blue", "red"), env.sessions.getTimenoteById("a")!!.tags.map { it.id })
        assertEquals(listOf("red"), env.sessions.getTimenoteById("b")!!.tags.map { it.id })
        assertEquals(setOf("a", "b"), env.vm.selectedIds.value, "more tags can be changed before closing the sheet")

        env.vm.toggleTagOnSelected(red) // now on all of them: takes it off
        assertEquals(listOf("blue"), env.sessions.getTimenoteById("a")!!.tags.map { it.id })
        assertTrue(env.sessions.getTimenoteById("b")!!.tags.isEmpty())
    }
}
