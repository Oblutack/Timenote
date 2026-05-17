package com.oblutack.timenote.feature_settings.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun PremiumColorWheel(
    modifier: Modifier = Modifier,
    onColorChanged: (Color) -> Unit
) {
    var touchPosition by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    touchPosition = change.position
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    touchPosition = offset
                }
            }
    ) {
        val radius = size.width / 2
        val center = Offset(radius, radius)

        // 1. Draw the Rainbow Hue Sweep
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red, Color.Magenta, Color.Blue, Color.Cyan,
                    Color.Green, Color.Yellow, Color.Red
                ),
                center = center
            ),
            radius = radius
        )

        // 2. Draw the White Saturation Fade (Center to Edge)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius
        )

        // 3. Draw the interactive Thumb
        touchPosition?.let { pos ->
            // Keep the thumb mathematically inside the circle
            val dx = pos.x - center.x
            val dy = pos.y - center.y
            val dist = sqrt(dx * dx + dy * dy)
            val clampedDist = dist.coerceAtMost(radius)

            val angleRad = atan2(dy, dx)
            val clampedX = center.x + clampedDist * cos(angleRad)
            val clampedY = center.y + clampedDist * sin(angleRad)

            // Convert position to HSV
            var hue = (angleRad * 180f / PI).toFloat()
            if (hue < 0) hue += 360f
            val saturation = clampedDist / radius

            val newColor = Color.hsv(hue = hue, saturation = saturation, value = 1f)

            // Report color back to UI instantly
            onColorChanged(newColor)

            // Draw the Thumb border
            drawCircle(
                color = Color.White,
                radius = 16.dp.toPx(),
                center = Offset(clampedX, clampedY),
                style = Stroke(width = 3.dp.toPx())
            )
            // Draw the Thumb fill
            drawCircle(
                color = newColor,
                radius = 13.dp.toPx(),
                center = Offset(clampedX, clampedY)
            )
        }
    }
}