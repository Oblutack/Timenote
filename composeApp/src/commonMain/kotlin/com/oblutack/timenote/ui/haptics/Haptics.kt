package com.oblutack.timenote.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.oblutack.timenote.di.LocalAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The moments of the app that have a feel. Each one feels different, so you know what happened without looking. */
enum class Beat {
    /** A session starts, or a long-press opens a menu: one firm tap. */
    Start,
    /** A break begins: two light ticks. */
    Pause,
    /** A break ends, a session carries on, a note was saved or an entry removed: one light tick. */
    Tick,
    /** A session ends: the system's "confirmed" effect. */
    End
}

/** The three kinds of feedback the phone offers; [Haptics] maps them onto its own effects. */
enum class Feel { Firm, Light, Confirm }

/** One pulse of a pattern, played [afterMillis] after the one before it. */
data class Pulse(val feel: Feel, val afterMillis: Long = 0)

/**
 * What each [Beat] feels like. Only the phone's own effects are used, so it feels native and needs no permission. The two ticks of a
 * pause are 160 ms apart because one tick lasts about 100 ms: closer together the second cuts the first short.
 */
fun pulsesFor(beat: Beat): List<Pulse> = when (beat) {
    Beat.Start -> listOf(Pulse(Feel.Firm))
    Beat.Pause -> listOf(Pulse(Feel.Light), Pulse(Feel.Light, afterMillis = 160))
    Beat.Tick -> listOf(Pulse(Feel.Light))
    Beat.End -> listOf(Pulse(Feel.Confirm))
}

/** Plays [Beat]s, unless the person turned haptics off in Settings. */
class Haptics(
    private val feedback: HapticFeedback,
    private val enabled: () -> Boolean,
    private val scope: CoroutineScope
) {
    fun play(beat: Beat) {
        if (!enabled()) return
        val pulses = pulsesFor(beat)
        scope.launch {
            pulses.forEach { pulse ->
                if (pulse.afterMillis > 0) delay(pulse.afterMillis)
                feedback.performHapticFeedback(pulse.feel.toType())
            }
        }
    }
}

private fun Feel.toType(): HapticFeedbackType = when (this) {
    Feel.Firm -> HapticFeedbackType.LongPress
    Feel.Light -> HapticFeedbackType.ContextClick
    Feel.Confirm -> HapticFeedbackType.Confirm
}

/** The app's haptics, following the "Haptic Feedback" switch in Settings. */
@Composable
fun rememberHaptics(): Haptics {
    val settings = LocalAppContainer.current.settingsRepository
    val enabled by settings.enableHapticsFlow.collectAsState(initial = true)
    val latest = rememberUpdatedState(enabled)
    val feedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(feedback, scope) { Haptics(feedback, { latest.value }, scope) }
}
