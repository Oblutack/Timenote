package com.oblutack.timenote.feature_history

import com.oblutack.timenote.feature_history.domain.findMatchRanges
import com.oblutack.timenote.feature_history.domain.searchSnippet
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.testutil.testNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchMatchTest {
    @Test fun everyOccurrenceIsFoundIgnoringCase() {
        assertEquals(listOf(0..2, 8..10), findMatchRanges("Cat and cAt", "cat"))
    }

    @Test fun noQueryOrNoMatchFindsNothing() {
        assertTrue(findMatchRanges("anything", "").isEmpty())
        assertTrue(findMatchRanges("anything", "  ").isEmpty())
        assertTrue(findMatchRanges("anything", "zzz").isEmpty())
    }

    @Test fun theDescriptionMatchShowsItsWordsAndWhereTheyAre() {
        val note = testNote("a").copy(description = "We **decided** on the new budget today")
        val snippet = searchSnippet(note, "budget")!!
        assertEquals("We decided on the new budget today", snippet.text)
        assertEquals("budget", snippet.ranges.map { snippet.text.substring(it) }.single())
    }

    @Test fun aNoteIsSearchedWhenTheDescriptionDoesNotMatch() {
        val note = testNote("a").copy(
            description = "unrelated",
            timelineEvents = listOf(TimelineEvent("1", "Note: call the plumber", "00:05:00", EventType.NOTE))
        )
        assertEquals("call the plumber", searchSnippet(note, "plumber")!!.text)
    }

    @Test fun theDescriptionComesBeforeTheNotes() {
        val note = testNote("a").copy(
            description = "plumber in the description",
            timelineEvents = listOf(TimelineEvent("1", "Note: plumber in a note", "00:05:00", EventType.NOTE))
        )
        assertEquals("plumber in the description", searchSnippet(note, "plumber")!!.text)
    }

    @Test fun aTitleOnlyMatchHasNoSnippet() {
        assertNull(searchSnippet(testNote("a", title = "Budget review"), "budget"))
    }

    @Test fun aLongLineIsCutAroundTheMatchWithDots() {
        val note = testNote("a").copy(description = "x".repeat(100) + " needle " + "y".repeat(100))
        val snippet = searchSnippet(note, "needle")!!
        assertTrue(snippet.text.startsWith("…") && snippet.text.endsWith("…"))
        assertEquals("needle", snippet.ranges.map { snippet.text.substring(it) }.single())
        assertTrue(snippet.text.length < 100)
    }

    @Test fun markdownDoesNotGetInTheWayOfAMatch() {
        val note = testNote("a").copy(description = "see @[Weekly review](id1) soon")
        assertEquals("see Weekly review soon", searchSnippet(note, "weekly")!!.text)
    }

    @Test fun theBranchLineIsNotAMatch() {
        val note = testNote("a").copy(timelineEvents = listOf(TimelineEvent("1", "Branched from: Note: first idea (Parent)", "00:00:00", EventType.NOTE)))
        assertNull(searchSnippet(note, "idea"))
    }

    @Test fun aVoiceMemoEntryIsNotSearchedAsText() {
        val note = testNote("a").copy(timelineEvents = listOf(TimelineEvent("1", "Voice Memo attached", "00:01:00", EventType.NOTE, audioPath = "m.m4a")))
        assertNull(searchSnippet(note, "memo"))
    }
}
