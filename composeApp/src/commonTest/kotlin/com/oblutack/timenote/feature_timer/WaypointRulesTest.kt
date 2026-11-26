package com.oblutack.timenote.feature_timer

import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.feature_timer.domain.canDeleteWhileRunning
import com.oblutack.timenote.feature_timer.domain.canEditWhileRunning
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaypointRulesTest {
    private fun event(type: EventType, title: String = "x", audio: String? = null) =
        TimelineEvent("id", title, "00:00:01", type, audioPath = audio)

    @Test fun aTypedNoteCanBeEditedAndDeleted() {
        val note = event(EventType.NOTE, "Note: hello")
        assertTrue(note.canEditWhileRunning())
        assertTrue(note.canDeleteWhileRunning())
    }

    @Test fun aVoiceMemoCanOnlyBeDeleted() {
        val memo = event(EventType.NOTE, "Voice Memo attached", audio = "VoiceMemo_1.m4a")
        assertFalse(memo.canEditWhileRunning())
        assertTrue(memo.canDeleteWhileRunning())
    }

    @Test fun theBranchNoteIsFixed() {
        val branch = event(EventType.NOTE, "Branched from: Idea (Parent)")
        assertFalse(branch.canEditWhileRunning())
        assertFalse(branch.canDeleteWhileRunning())
    }

    @Test fun entriesFromTheClockAreFixed() {
        listOf(EventType.START, EventType.PAUSE, EventType.RESUME, EventType.END).forEach { type ->
            assertFalse(event(type).canEditWhileRunning(), "$type")
            assertFalse(event(type).canDeleteWhileRunning(), "$type")
        }
    }
}
