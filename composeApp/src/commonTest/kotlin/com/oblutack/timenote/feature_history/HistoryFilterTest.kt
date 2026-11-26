package com.oblutack.timenote.feature_history

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.feature_history.domain.DateFilter
import com.oblutack.timenote.feature_history.domain.HistoryFilter
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.filterSessions
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.testutil.testNote
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryFilterTest {
    private val utc = TimeZone.UTC
    private val today = LocalDate(2026, 11, 26)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()
    private fun tag(id: String) = TimenoteFolder(id, id, null, 0, Color.Red)
    private fun note(id: String, iso: String = "2026-11-26T10:00:00Z") = testNote(id, createdAt = at(iso))
    private fun ids(list: List<Timenote>) = list.map { it.id }
    private fun run(all: List<Timenote>, filter: HistoryFilter = HistoryFilter(), query: String = "") =
        ids(filterSessions(all, filter, query, today, utc, at("2026-11-26T12:00:00Z")))

    private val all = listOf(
        note("today", "2026-11-26T08:00:00Z"),
        note("yesterday", "2026-11-25T08:00:00Z"),
        note("lastWeek", "2026-11-20T08:00:00Z"),
        note("lastMonth", "2026-11-01T08:00:00Z"),
        note("october", "2026-10-30T08:00:00Z"),
        note("old", "2026-06-01T08:00:00Z")
    )

    @Test fun noFilterKeepsEverythingInOrder() = assertEquals(ids(all), run(all))

    @Test fun todayLastSevenLastThirtyAndThisMonth() {
        assertEquals(listOf("today"), run(all, HistoryFilter(date = DateFilter.Today)))
        // today and the six days before it: 20 Nov is 6 days back, so it is in
        assertEquals(listOf("today", "yesterday", "lastWeek"), run(all, HistoryFilter(date = DateFilter.Last7Days)))
        assertEquals(listOf("today", "yesterday", "lastWeek", "lastMonth", "october"), run(all, HistoryFilter(date = DateFilter.Last30Days)))
        assertEquals(listOf("today", "yesterday", "lastWeek", "lastMonth"), run(all, HistoryFilter(date = DateFilter.ThisMonth)))
    }

    @Test fun theSevenDayWindowEndsExactlySixDaysBack() {
        val edge = listOf(note("in", "2026-11-20T00:00:00Z"), note("out", "2026-11-19T23:59:00Z"))
        assertEquals(listOf("in"), run(edge, HistoryFilter(date = DateFilter.Last7Days)))
    }

    @Test fun aPickedDayShowsOnlyThatDay() {
        assertEquals(listOf("yesterday"), run(all, HistoryFilter(date = DateFilter.Day(LocalDate(2026, 11, 25)))))
    }

    @Test fun thePhonesTimeZoneDecidesTheDay() {
        val late = listOf(note("late", "2026-11-25T23:30:00Z")) // 00:30 on the 26th in Berlin
        val berlin = TimeZone.of("Europe/Berlin")
        assertEquals(listOf("late"), ids(filterSessions(late, HistoryFilter(date = DateFilter.Today), "", today, berlin, 0L)))
        assertTrue(run(late, HistoryFilter(date = DateFilter.Today)).isEmpty())
    }

    @Test fun aFolderKeepsOnlyItsSessions() {
        val list = listOf(note("a").copy(folderId = "f1"), note("b").copy(folderId = "f2"), note("c"))
        assertEquals(listOf("a"), run(list, HistoryFilter(folderId = "f1")))
    }

    @Test fun tagsMatchWhenAnyOfThemIsThere() {
        val list = listOf(note("a").copy(tags = listOf(tag("t1"))), note("b").copy(tags = listOf(tag("t2"))), note("c"))
        assertEquals(listOf("a", "b"), run(list, HistoryFilter(tagIds = setOf("t1", "t2"))))
        assertEquals(listOf("b"), run(list, HistoryFilter(tagIds = setOf("t2"))))
    }

    @Test fun hasNotesCountsWrittenNotesOnly() {
        val written = note("w").copy(timelineEvents = listOf(TimelineEvent("1", "Note: hi", "00:01:00", EventType.NOTE)))
        val branchOnly = note("b").copy(timelineEvents = listOf(TimelineEvent("1", "Branched from: X (Y)", "00:00:00", EventType.NOTE)))
        val clockOnly = note("c").copy(timelineEvents = listOf(TimelineEvent("1", "Session Started", "00:00:00", EventType.START)))
        assertEquals(listOf("w"), run(listOf(written, branchOnly, clockOnly), HistoryFilter(hasNotes = true)))
    }

    @Test fun hasVoiceFindsAttachedAndRecordedMemos() {
        val attached = note("a").copy(voiceNotes = listOf("memo.m4a"))
        val recorded = note("b").copy(timelineEvents = listOf(TimelineEvent("1", "Voice Memo attached", "00:01:00", EventType.NOTE, audioPath = "m.m4a")))
        assertEquals(listOf("a", "b"), run(listOf(attached, recorded, note("c")), HistoryFilter(hasVoice = true)))
    }

    @Test fun hasBranchesKeepsTheSessionsThatOthersBranchedFrom() {
        val parent = note("parent")
        val child = note("child").copy(parentTimenoteId = "parent")
        val alone = note("alone")
        assertEquals(listOf("parent"), run(listOf(parent, child, alone), HistoryFilter(hasBranches = true)))
    }

    @Test fun pinnedOnly() {
        assertEquals(listOf("p"), run(listOf(note("p").copy(isPinned = true), note("q")), HistoryFilter(pinnedOnly = true)))
    }

    @Test fun searchWordsLookInTitleDescriptionTagsAndNotes() {
        val list = listOf(
            note("title").copy(title = "Quarterly Review"),
            note("desc").copy(description = "plans for the review"),
            note("tag").copy(tags = listOf(TimenoteFolder("t", "Reviewing", null, 0, Color.Red))),
            note("note").copy(timelineEvents = listOf(TimelineEvent("1", "Note: reviewed it", "00:01:00", EventType.NOTE))),
            note("none").copy(title = "Other")
        )
        assertEquals(listOf("title", "desc", "tag", "note"), run(list, query = "REVIEW"))
    }

    @Test fun filtersAndSearchWorkTogether() {
        val list = listOf(
            note("a", "2026-11-26T08:00:00Z").copy(title = "Plan", isPinned = true),
            note("b", "2026-11-26T09:00:00Z").copy(title = "Plan"),
            note("c", "2026-11-26T10:00:00Z").copy(title = "Other", isPinned = true)
        )
        assertEquals(listOf("a"), run(list, HistoryFilter(pinnedOnly = true, date = DateFilter.Today), "plan"))
    }

    @Test fun isActiveIgnoresNothingButTheSearchWords() {
        assertFalse(HistoryFilter().isActive)
        assertTrue(HistoryFilter(hasVoice = true).isActive)
        assertTrue(HistoryFilter(date = DateFilter.Today).isActive)
        assertTrue(HistoryFilter(tagIds = setOf("x")).isActive)
        assertTrue(HistoryFilter(folderId = "f").isActive)
    }
}
