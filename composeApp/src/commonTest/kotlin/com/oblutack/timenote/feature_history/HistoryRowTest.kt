package com.oblutack.timenote.feature_history

import com.oblutack.timenote.feature_history.domain.HistoryDensity
import com.oblutack.timenote.feature_history.domain.noteCountOf
import com.oblutack.timenote.feature_history.presentation.sessionFactsLine
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.testutil.testNote
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryRowTest {
    private fun facts(
        folder: String? = null, notes: Int = 0, checklist: Pair<Int, Int>? = null,
        voice: Boolean = false, branch: Boolean = false, branches: Int = 0
    ) = sessionFactsLine("6:40 – 7:28 PM", folder, notes, checklist, voice, branch, branches)

    @Test fun aBareSessionShowsOnlyItsTimes() = assertEquals("6:40 – 7:28 PM", facts())

    @Test fun everythingThatIsThereIsListedInOneLine() {
        assertEquals(
            "6:40 – 7:28 PM · Thesis · 3 notes · ☑ 2/4 · Voice · ↳ Branch · 2 branches",
            facts(folder = "Thesis", notes = 3, checklist = 2 to 4, voice = true, branch = true, branches = 2)
        )
    }

    @Test fun oneBranchIsSingular() = assertEquals("6:40 – 7:28 PM · 1 branch", facts(branches = 1))

    @Test fun oneNoteIsSingular() = assertEquals("6:40 – 7:28 PM · 1 note", facts(notes = 1))

    @Test fun emptyPartsAreLeftOut() = assertEquals("6:40 – 7:28 PM · Thesis", facts(folder = "Thesis", notes = 0))

    @Test fun onlyWrittenNotesAreCounted() {
        val session = testNote("a").copy(timelineEvents = listOf(
            TimelineEvent("1", "Session Started", "00:00:00", EventType.START),
            TimelineEvent("2", "Note: one", "00:01:00", EventType.NOTE),
            TimelineEvent("3", "Branched from: Outline (Parent)", "00:01:00", EventType.NOTE),
            TimelineEvent("4", "Note: Interrupted", "00:02:00", EventType.NOTE),
            TimelineEvent("5", "Paused", "00:03:00", EventType.PAUSE)
        ))
        assertEquals(2, noteCountOf(session))
    }

    @Test fun theDensityDefaultsToCompactAndUnknownValuesToo() {
        assertEquals(HistoryDensity.Compact, HistoryDensity.fromKey(null))
        assertEquals(HistoryDensity.Compact, HistoryDensity.fromKey("tiny"))
        assertEquals(HistoryDensity.Comfortable, HistoryDensity.fromKey("comfortable"))
    }
}
