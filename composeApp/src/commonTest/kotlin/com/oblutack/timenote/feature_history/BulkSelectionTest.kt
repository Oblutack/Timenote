package com.oblutack.timenote.feature_history

import com.oblutack.timenote.feature_history.domain.TagCoverage
import com.oblutack.timenote.feature_history.domain.branchesOutside
import com.oblutack.timenote.feature_history.domain.shouldPinAll
import com.oblutack.timenote.feature_history.domain.tagCoverage
import com.oblutack.timenote.feature_history.domain.tagsAfterBulkToggle
import com.oblutack.timenote.feature_history.domain.trashMessage
import com.oblutack.timenote.feature_history.presentation.selectedCountLabel
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BulkSelectionTest {
    private val red = testTag("red")
    private val blue = testTag("blue")

    @Test fun aTagIsOnNoneSomeOrAllOfTheSelectedSessions() {
        val withRed = testNote("a").copy(tags = listOf(red))
        val without = testNote("b")
        assertEquals(TagCoverage.None, tagCoverage(listOf(without, without), "red"))
        assertEquals(TagCoverage.Some, tagCoverage(listOf(withRed, without), "red"))
        assertEquals(TagCoverage.All, tagCoverage(listOf(withRed, withRed), "red"))
        assertEquals(TagCoverage.None, tagCoverage(emptyList(), "red"))
    }

    @Test fun tappingATagOnSomePutsItOnAllAndKeepsTheOtherTags() {
        val has = testNote("a").copy(tags = listOf(red, blue))
        val lacks = testNote("b").copy(tags = listOf(blue))
        assertEquals(listOf(red, blue), tagsAfterBulkToggle(has, red, TagCoverage.Some), "already there: unchanged, not doubled")
        assertEquals(listOf(blue, red), tagsAfterBulkToggle(lacks, red, TagCoverage.Some))
        assertEquals(listOf(red), tagsAfterBulkToggle(testNote("c"), red, TagCoverage.None))
    }

    @Test fun tappingATagOnAllTakesItOffAllAndKeepsTheOthers() {
        val has = testNote("a").copy(tags = listOf(red, blue))
        assertEquals(listOf(blue), tagsAfterBulkToggle(has, red, TagCoverage.All))
    }

    @Test fun pinPinsEverythingUnlessEverythingIsPinned() {
        val pinned = testNote("a").copy(isPinned = true)
        val loose = testNote("b")
        assertTrue(shouldPinAll(listOf(pinned, loose)))
        assertTrue(shouldPinAll(listOf(loose)))
        assertFalse(shouldPinAll(listOf(pinned, pinned)))
    }

    @Test fun branchesOutsideAreTheDescendantsThatAreNotSelected() {
        val all = listOf(testNote("p"), testNote("c", "p"), testNote("g", "c"), testNote("other"))
        assertEquals(setOf("c", "g"), branchesOutside(all, setOf("p")))
        assertEquals(setOf("g"), branchesOutside(all, setOf("p", "c")))
        assertTrue(branchesOutside(all, setOf("p", "c", "g")).isEmpty(), "a whole branch selected leaves nothing behind")
        assertTrue(branchesOutside(all, setOf("other")).isEmpty())
    }

    @Test fun theTrashMessageNamesOneSessionAndCountsSeveral() {
        assertEquals("\"Morning\" moved to trash", trashMessage(listOf(testNote("a", title = "Morning")), 1))
        assertEquals("\"Untitled\" moved to trash", trashMessage(listOf(testNote("a", title = "")), 1))
        assertEquals("3 sessions moved to trash", trashMessage(listOf(testNote("a"), testNote("b"), testNote("c")), 3))
    }

    @Test fun theCountLabel() {
        assertEquals("1 selected", selectedCountLabel(1))
        assertEquals("12 selected", selectedCountLabel(12))
    }
}
