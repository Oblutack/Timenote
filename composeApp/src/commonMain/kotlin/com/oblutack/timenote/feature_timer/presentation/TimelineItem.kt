package com.oblutack.timenote.feature_timer.presentation
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import androidx.compose.ui.draw.alpha
import com.oblutack.timenote.feature_timer.domain.EventType
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.drawBehind

@Composable
fun TimelineItem(event: TimelineEvent, isLastItem: Boolean, useMonochrome: Boolean) { // <--- ADD useMonochrome: Boolean

    // 1. Sleek Fade-In Animation (Doesn't break list height!)
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        alpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(500))
    }

    val isStartOrEnd = event.type == EventType.START || event.type == EventType.END
    val circleRadius = if (isStartOrEnd) 7.dp else 5.dp
    val nodeColor = when (event.type) {
        EventType.START -> if (useMonochrome) TextPrimary else TnTheme.colors.success
        EventType.END -> if (useMonochrome) TextSecondary else TnTheme.colors.danger
        else -> event.color ?: DefaultAccentColor
    }

    val connectorColor = SurfaceDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha.value) // Applies the fade-in
            .padding(bottom = 8.dp)
            // 2. Draw the vertical line in the background (Removes the need for IntrinsicSize.Min!)
            .drawBehind {
                if (!isLastItem) {
                    val circleCenterY = 10.dp.toPx()
                    val lineStartY = circleCenterY + circleRadius.toPx() + 4.dp.toPx()
                    // Draw line straight down based on the actual height of the text column
                    drawLine(
                        color = connectorColor,
                        start = Offset(12.dp.toPx(), lineStartY),
                        end = Offset(12.dp.toPx(), size.height + 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            },
        verticalAlignment = Alignment.Top
    ) {
        // 3. Just draw the circle here, no fillMaxHeight needed
        Box(
            modifier = Modifier.width(24.dp).padding(top = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                drawCircle(
                    color = nodeColor,
                    radius = circleRadius.toPx(),
                    center = Offset(size.width / 2, 0f),
                    style = Stroke(width = if (isStartOrEnd) 2.dp.toPx() else 1.5.dp.toPx())
                )
                drawCircle(
                    color = nodeColor,
                    radius = circleRadius.toPx() * 0.5f,
                    center = Offset(size.width / 2, 0f)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 4. The Text Column dictates the natural height of the Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = event.title,
                color = TextPrimary,
                fontSize = TnType.BodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.timestamp,
                color = TextSecondary,
                fontSize = TnType.Body
            )
        }
    }
}
