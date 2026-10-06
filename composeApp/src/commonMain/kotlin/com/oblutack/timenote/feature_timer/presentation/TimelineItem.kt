package com.oblutack.timenote.feature_timer.presentation
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.oblutack.timenote.feature_history.domain.mockFolders
import com.oblutack.timenote.feature_timer.domain.EventType
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur

@Composable
fun TimelineItem(event: com.oblutack.timenote.feature_timer.domain.TimelineEvent, isLastItem: Boolean, useMonochrome: Boolean) { // <--- ADD useMonochrome: Boolean

    // 1. Sleek Fade-In Animation (Doesn't break list height!)
    val alpha = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(event.id) {
        alpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(500))
    }

    val isStartOrEnd = event.type == com.oblutack.timenote.feature_timer.domain.EventType.START || event.type == com.oblutack.timenote.feature_timer.domain.EventType.END
    val circleRadius = if (isStartOrEnd) 7.dp else 5.dp
    val nodeColor = when (event.type) {
        com.oblutack.timenote.feature_timer.domain.EventType.START -> if (useMonochrome) TextPrimary else Color(0xFF4CAF50)
        com.oblutack.timenote.feature_timer.domain.EventType.END -> if (useMonochrome) TextSecondary else Color(0xFFE53935)
        else -> event.color ?: DefaultAccentColor
    }

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
                        color = SurfaceDark,
                        start = androidx.compose.ui.geometry.Offset(12.dp.toPx(), lineStartY),
                        end = androidx.compose.ui.geometry.Offset(12.dp.toPx(), size.height + 8.dp.toPx()),
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
            androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
                drawCircle(
                    color = nodeColor,
                    radius = circleRadius.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, 0f),
                    style = Stroke(width = if (isStartOrEnd) 2.dp.toPx() else 1.5.dp.toPx())
                )
                drawCircle(
                    color = nodeColor,
                    radius = circleRadius.toPx() * 0.5f,
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, 0f)
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
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.timestamp,
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}
