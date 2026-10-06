package com.oblutack.timenote.core

import com.oblutack.timenote.feature_history.domain.Timenote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimenoteTreeTest {
    private fun note(id: String, parent: String? = null) = Timenote(
        id = id, title = id, description = "", duration = "00:00:00",
        activeSeconds = 0, pauseSeconds = 0, createdAt = 0L,
        tags = emptyList(), timelineEvents = emptyList(), parentTimenoteId = parent
    )

    @Test fun leafHasNoDescendants() =
        assertTrue(descendantIds(listOf(note("a")), "a").isEmpty())

    @Test fun findsAllLevels() {
        val notes = listOf(note("root"), note("c1", "root"), note("c2", "root"), note("gc", "c1"), note("other"))
        assertEquals(setOf("c1", "c2", "gc"), descendantIds(notes, "root").toSet())
    }

    @Test fun doesNotIncludeSiblingsOrAncestors() {
        val notes = listOf(note("root"), note("c1", "root"), note("c2", "root"))
        assertEquals(listOf<String>(), descendantIds(notes, "c1"))
    }

    @Test fun cyclicLinksTerminate() {
        val notes = listOf(note("a", "b"), note("b", "a"))
        assertEquals(listOf("b"), descendantIds(notes, "a"))
    }
}
