package com.oblutack.timenote.feature_timer.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oblutack.timenote.ui.theme.LocalPlatformTheme
import com.oblutack.timenote.ui.theme.TnTheme

/** One lap of the ring is one hour: without a goal it shows where you are inside the hour, then starts over. */
const val RING_LAP_SECONDS = 3600L

/** Above this system font scale the ring is left out: it would push the controls off the screen. */
const val RING_MAX_FONT_SCALE = 1.3f

/**
 * How much of the ring is filled, 0..1. A lap that has just started shows a small dot (not an empty ring), so a running
 * ring is never mistaken for an idle one.
 */
fun ringFraction(seconds: Long): Float {
    if (seconds <= 0L) return 0.004f
    val inLap = seconds % RING_LAP_SECONDS
    return if (inLap == 0L) 0.004f else inLap.toFloat() / RING_LAP_SECONDS
}

/**
 * The ring around the clock. [progress] null draws only the quiet track (idle). The colour says what is happening: the
 * session colour while running, the pause colour on a break. While running it breathes very slightly, unless the phone
 * has animations turned off.
 */
@Composable
fun TimerRing(
    progress: Float?,
    color: Color,
    breathing: Boolean,
    size: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    val track = TnTheme.colors.surfaceRaised
    val animationsOn = LocalPlatformTheme.current.animationsEnabled()
    val breath = if (breathing && animationsOn) {
        val transition = rememberInfiniteTransition(label = "ringBreath")
        transition.animateFloat(
            initialValue = 0.78f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 2000, easing = LinearEasing), RepeatMode.Reverse),
            label = "ringBreathAlpha"
        ).value
    } else 1f

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            if (progress != null) {
                drawArc(
                    color = color.copy(alpha = breath),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}
