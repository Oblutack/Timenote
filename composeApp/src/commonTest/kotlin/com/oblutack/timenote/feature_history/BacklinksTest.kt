package com.oblutack.timenote.feature_history

import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.findBacklinks
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.testutil.testNote
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BacklinksTest {
    private val utc = TimeZone.UTC
    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()
    private fun links(target: String, vararg sessions: Timenote) = findBacklinks(target, sessions.toList(), utc, 2026)

    @Test fun aDescriptionThatMentionsTheSessionIsListedWithItsWords() {
        val a = testNote("a", title = "Target")
        val b = testNote("b", title = "Weekly review", createdAt = at("2026-11-20T10:00:00Z")).copy(
            description = "Compared the plan with @[Target](a) and moved on."
        )
        val result = links("a", a, b)
        assertEquals(1, result.size)
        assertEquals("b", result.single().sessionId)
        assertEquals("Weekly review", result.single().title)
        assertEquals("Compared the plan with Target and moved on.", result.single().snippet)
        assertEquals("Fri 20 Nov", result.single().dateText)
    }

    @Test fun aNoteThatMentionsItCountsToo() {
        val a = testNote("a")
        val b = testNote("b", title = "Call").copy(
            timelineEvents = listOf(TimelineEvent("e1", "Note: see @[Target](a) first", "00:05:00", EventType.NOTE))
        )
        assertEquals("see Target first", links("a", a, b).single().snippet)
    }

    @Test fun aSessionIsListedOnceEvenWhenItMentionsTheTargetSeveralTimes() {
        val b = testNote("b").copy(description = "One @[T](a)\nTwo @[T](a)")
        val result = links("a", testNote("a"), b)
        assertEquals(1, result.size)
        assertEquals("One T", result.single().snippet, "the first mention gives the snippet")
    }

    @Test fun otherMentionsAndTheSessionItselfAreIgnored() {
        val a = testNote("a").copy(description = "I mention myself @[Me](a)")
        val b = testNote("b").copy(description = "Only @[Someone else](c)")
        assertTrue(links("a", a, b).isEmpty())
    }

    @Test fun sessionsInTheTrashDoNotCount() {
        val gone = testNote("b").copy(description = "@[T](a)", isDeleted = true)
        assertTrue(links("a", testNote("a"), gone).isEmpty())
    }

    @Test fun theNewestMentionComesFirst() {
        val old = testNote("old", createdAt = at("2026-01-01T10:00:00Z")).copy(description = "@[T](a)")
        val new = testNote("new", createdAt = at("2026-11-01T10:00:00Z")).copy(description = "@[T](a)")
        assertEquals(listOf("new", "old"), links("a", testNote("a"), old, new).map { it.sessionId })
    }

    @Test fun aLongLineIsCutAroundTheMention() {
        val long = "x".repeat(100) + " @[Target](a) " + "y".repeat(100)
        val snippet = links("a", testNote("a"), testNote("b").copy(description = long)).single().snippet
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"), snippet)
        assertTrue("Target" in snippet)
        assertTrue(snippet.length < 100, "kept short: ${snippet.length}")
    }

    @Test fun markdownSymbolsAreNotShownInTheSnippet() {
        val b = testNote("b").copy(description = "- [ ] **Ask** about @[Target](a) ~~later~~")
        assertEquals("Ask about Target later", links("a", testNote("a"), b).single().snippet)
    }

    @Test fun aBlankTitleGetsTheUsualName() {
        val b = testNote("b", title = " ").copy(description = "@[T](a)")
        assertEquals("Untitled Session", links("a", testNote("a"), b).single().title)
    }
}
