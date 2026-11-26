package com.oblutack.timenote.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.oblutack.timenote.ui.haptics.Beat
import com.oblutack.timenote.ui.haptics.Haptics
import com.oblutack.timenote.ui.haptics.pulsesFor
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HapticsTest {
    private class Recorder : HapticFeedback {
        val played = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { played += hapticFeedbackType }
    }

    @Test fun everyMomentFeelsDifferentFromTheOthersThatMatter() {
        // start, pause and end must be told apart without looking
        val start = pulsesFor(Beat.Start)
        val pause = pulsesFor(Beat.Pause)
        val end = pulsesFor(Beat.End)
        assertNotEquals(start, pause)
        assertNotEquals(start, end)
        assertNotEquals(pause, end)
        assertEquals(1, start.size)
        assertEquals(2, pause.size, "a pause is a double tick")
        assertEquals(1, end.size)
    }

    @Test fun startIsOneFirmTap() = runTest {
        val feedback = Recorder()
        Haptics(feedback, { true }, this).play(Beat.Start)
        runCurrent()
        assertEquals(listOf(HapticFeedbackType.LongPress), feedback.played)
    }

    @Test fun aPauseIsTwoLightTicksWithAGapBetween() = runTest {
        val feedback = Recorder()
        Haptics(feedback, { true }, this).play(Beat.Pause)
        runCurrent()
        assertEquals(1, feedback.played.size, "the first tick is immediate")
        advanceTimeBy(159)
        assertEquals(1, feedback.played.size, "the second one waits")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(List(2) { HapticFeedbackType.ContextClick }, feedback.played)
    }

    @Test fun theEndIsTheConfirmedEffect() = runTest {
        val feedback = Recorder()
        Haptics(feedback, { true }, this).play(Beat.End)
        runCurrent()
        assertEquals(listOf(HapticFeedbackType.Confirm), feedback.played)
    }

    @Test fun nothingPlaysWhenHapticsAreTurnedOff() = runTest {
        val feedback = Recorder()
        val haptics = Haptics(feedback, { false }, this)
        Beat.entries.forEach { haptics.play(it) }
        runCurrent()
        advanceTimeBy(1_000)
        assertTrue(feedback.played.isEmpty())
    }

    @Test fun theSwitchIsReadWhenAMomentHappensNotWhenTheScreenIsBuilt() = runTest {
        val feedback = Recorder()
        var on = false
        val haptics = Haptics(feedback, { on }, this)
        haptics.play(Beat.Tick)
        runCurrent()
        assertTrue(feedback.played.isEmpty())
        on = true
        haptics.play(Beat.Tick)
        runCurrent()
        assertEquals(1, feedback.played.size)
    }
}
