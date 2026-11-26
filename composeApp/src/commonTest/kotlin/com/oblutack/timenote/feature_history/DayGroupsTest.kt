package com.oblutack.timenote.feature_history

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.checklistProgress
import com.oblutack.timenote.feature_history.domain.groupSessionsByDay
import com.oblutack.timenote.testutil.testNote
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayGroupsTest {
    private val utc = TimeZone.UTC
    private val today = LocalDate(2026, 11, 26)
    private val grey = Color(0xFF808080)
    private val green = Color(0xFF00AA00)
    private val blue = Color(0xFF0000AA)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()
    private fun note(id: String, iso: String, focus: Int, tag: Color? = null, pinned: Boolean = false): Timenote =
        testNote(id, createdAt = at(iso), activeSeconds = focus).copy(
            isPinned = pinned,
            tags = if (tag == null) emptyList() else listOf(TimenoteFolder("t", "tag", null, 0, tag))
        )
    private fun group(vararg sessions: Timenote) = groupSessionsByDay(sessions.toList(), today, utc, at("2026-11-26T12:00:00Z"), grey)

    @Test fun todayAndYesterdayAreNamedAndOlderDaysAreDated() {
        val groups = group(
            note("a", "2026-11-26T10:00:00Z", 60),
            note("b", "2026-11-25T10:00:00Z", 60),
            note("c", "2026-11-20T10:00:00Z", 60)
        )
        assertEquals(listOf("Today", "Yesterday", "Fri 20 Nov"), groups.map { it.title })
    }

    @Test fun anotherYearIsShownInTheTitle() {
        assertEquals("Tue 25 Nov 2025", group(note("a", "2025-11-25T10:00:00Z", 60)).single().title)
    }

    @Test fun aDayAddsUpItsFocusTimeOnly() {
        val g = group(
            note("a", "2026-11-26T10:00:00Z", 3_600),
            note("b", "2026-11-26T14:00:00Z", 1_800).copy(pauseSeconds = 3_000)
        ).single()
        assertEquals(5_400, g.focusSeconds)
        assertEquals("Today · 1 h 30 min", g.headerText)
    }

    @Test fun theOrderOfSessionsAndDaysIsKept() {
        val groups = group(
            note("new", "2026-11-26T10:00:00Z", 1),
            note("mid", "2026-11-25T10:00:00Z", 1),
            note("new2", "2026-11-26T08:00:00Z", 1)
        )
        // days appear in the order they first appear in the sorted list; a session joins its own day
        assertEquals(listOf("Today", "Yesterday"), groups.map { it.title })
        assertEquals(listOf("new", "new2"), groups.first().sessions.map { it.id })
    }

    @Test fun pinnedSessionsComeFirstInTheirOwnGroupWithoutATotal() {
        val groups = group(
            note("p", "2026-10-01T10:00:00Z", 600, pinned = true),
            note("a", "2026-11-26T10:00:00Z", 60)
        )
        assertEquals("Pinned", groups.first().title)
        assertNull(groups.first().date)
        assertEquals("Pinned", groups.first().headerText)
        assertEquals(listOf("p"), groups.first().sessions.map { it.id })
        assertEquals("Today", groups[1].title)
    }

    @Test fun theColourBarFollowsTheFocusTimePerTag() {
        val g = group(
            note("a", "2026-11-26T10:00:00Z", 3_000, green),
            note("b", "2026-11-26T11:00:00Z", 1_000, blue),
            note("c", "2026-11-26T12:00:00Z", 500, green),
            note("d", "2026-11-26T13:00:00Z", 200)
        ).single()
        assertEquals(listOf(green to 3_500, blue to 1_000, grey to 200), g.colorBar.map { it.color to it.seconds })
    }

    @Test fun theDayIsDecidedInTheLocalTimeZone() {
        val berlin = TimeZone.of("Europe/Berlin") // UTC+1: 23:30 UTC on the 25th is 00:30 on the 26th
        val groups = groupSessionsByDay(listOf(note("a", "2026-11-25T23:30:00Z", 60)), today, berlin, 0L, grey)
        assertEquals("Today", groups.single().title)
    }

    @Test fun anEmptyListHasNoGroups() = assertTrue(group().isEmpty())

    @Test fun checklistProgressCountsTickedBoxes() {
        assertEquals(2 to 4, checklistProgress("Plan\n- [x] one\n- [ ] two\n  * [X] three\n- [ ] four\nnot - [ ] a box"))
        assertEquals(0 to 1, checklistProgress("- [ ] only"))
    }

    @Test fun noBoxesMeansNoProgress() {
        assertNull(checklistProgress("just text - [ ] inside a line"))
        assertNull(checklistProgress(""))
    }
}
