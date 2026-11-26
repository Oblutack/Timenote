package com.oblutack.timenote.feature_history

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.core.SessionHeader
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.sessionAsMarkdown
import com.oblutack.timenote.feature_history.domain.sessionAsText
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.testutil.testNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionExportTest {
    private val header = SessionHeader("Wed 25 Nov", "6:40 – 7:28 PM")
    private fun event(id: String, title: String, time: String, type: EventType = EventType.NOTE, audio: String? = null) =
        TimelineEvent(id, title, time, type, audioPath = audio)

    // stored newest first, as the app keeps them
    private val full = testNote("a", title = "Thesis chapter 3", activeSeconds = 2_470, pauseSeconds = 410).copy(
        description = "Rewrote the **introduction**.",
        tags = listOf(TimenoteFolder("t1", "Writing", null, 0, Color.Red), TimenoteFolder("t2", "Deep work", null, 0, Color.Blue)),
        timelineEvents = listOf(
            event("e5", "Session Ended: Thesis chapter 3", "00:48:00", EventType.END),
            event("e4", "Voice Memo attached", "00:30:00", audio = "memo.m4a"),
            event("e3", "Note: second idea", "00:20:00"),
            event("e2", "Branched from: Outline (Parent)", "00:10:00"),
            event("e1", "Note: first idea", "00:05:00"),
            event("e0", "Session Started", "00:00:00", EventType.START)
        )
    )

    @Test fun textHasTheHeaderTotalsDescriptionAndNotesOldestFirst() {
        val text = sessionAsText(full, "Master's Thesis", header)
        assertEquals(
            """
            Thesis chapter 3
            Wed 25 Nov · 6:40 – 7:28 PM
            Folder: Master's Thesis
            Tags: Writing, Deep work
            Focus 00:41:10 · Breaks 00:06:50 · Total 00:48:00

            Rewrote the **introduction**.

            Notes
            00:05:00  first idea
            00:20:00  second idea
            00:30:00  Voice memo
            """.trimIndent(),
            text
        )
    }

    @Test fun markdownHasHeadingsAndAListOfNotes() {
        val md = sessionAsMarkdown(full, "Master's Thesis", header)
        assertEquals(
            """
            # Thesis chapter 3

            *Wed 25 Nov · 6:40 – 7:28 PM*

            - **Folder:** Master's Thesis
            - **Tags:** Writing, Deep work
            - **Focus:** 00:41:10
            - **Breaks:** 00:06:50
            - **Total:** 00:48:00

            Rewrote the **introduction**.

            ## Notes

            - `00:05:00` first idea
            - `00:20:00` second idea
            - `00:30:00` Voice memo
            """.trimIndent(),
            md
        )
    }

    @Test fun theClockEntriesAndTheBranchNoteAreNotListedAsNotes() {
        val text = sessionAsText(full, null, header)
        assertFalse("Session Started" in text)
        assertFalse("Session Ended" in text)
        assertFalse("Branched from" in text)
        assertFalse("Note: " in text, "the prefix is stripped")
    }

    @Test fun emptyPartsAreLeftOutAndABlankTitleGetsTheUsualName() {
        val bare = testNote("b", title = " ", activeSeconds = 60, pauseSeconds = 0)
        val text = sessionAsText(bare, null, header)
        assertTrue(text.startsWith("Untitled Session\n"))
        assertFalse("Folder:" in text)
        assertFalse("Tags:" in text)
        assertFalse("Notes" in text)
        val md = sessionAsMarkdown(bare, null, header)
        assertFalse("**Folder:**" in md)
        assertFalse("## Notes" in md)
    }

    @Test fun theOrderOfNotesDoesNotDependOnHowTheyAreStored() {
        val oldestFirst = full.copy(timelineEvents = full.timelineEvents.reversed())
        assertEquals(sessionAsText(full, null, header), sessionAsText(oldestFirst, null, header))
    }
}
