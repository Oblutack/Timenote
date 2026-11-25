package com.oblutack.timenote.feature_history.presentation
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.feature_history.domain.Timenote

@Composable
fun TimenoteTimelineItem(
    event: TimelineEvent,
    isLastItem: Boolean,
    useMonochrome: Boolean,
    playingAudioPath: String?,
    onPlayAudioClick: (String) -> Unit,
    onBranchClick: () -> Unit,
    childNotes: List<Timenote> = emptyList(),
    onChildClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
        ) {
            val startColor = if (useMonochrome) TextPrimary else TnTheme.colors.success
            val endColor = if (useMonochrome) TextSecondary else TnTheme.colors.danger
            val noteFallback = TnTheme.colors.untagged
            val breakColor = TnTheme.colors.pause
            val connectorColor = SurfaceDark
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 1. Determine size and color based on EventType
                val isStartOrEnd = event.type == EventType.START || event.type == EventType.END
                val circleRadius = if (isStartOrEnd) 7.dp.toPx() else 5.dp.toPx()
                val circleCenterY = 10.dp.toPx()

                val nodeColor = when (event.type) {
                    EventType.START -> startColor
                    EventType.END -> endColor
                    EventType.PAUSE, EventType.RESUME -> breakColor
                    else -> event.color ?: noteFallback
                }

                drawCircle(
                    color = nodeColor,
                    radius = circleRadius,
                    center = Offset(size.width / 2, circleCenterY),
                    style = Stroke(width = if (isStartOrEnd) 2.dp.toPx() else 1.5.dp.toPx())
                )

                drawCircle(
                    color = nodeColor,
                    radius = circleRadius * 0.5f,
                    center = Offset(size.width / 2, circleCenterY)
                )

                if (!isLastItem) {
                    val lineStartY = circleCenterY + circleRadius + 4.dp.toPx()
                    drawLine(
                        color = connectorColor,
                        start = Offset(size.width / 2, lineStartY),
                        end = Offset(size.width / 2, size.height + 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp, end = 8.dp)
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
                fontFamily = TnTheme.mono,
                fontSize = TnType.Body
            )

            if (event.audioPath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isPlaying = playingAudioPath == event.audioPath
                Row(
                    modifier = Modifier
                        .clip(TnShape.Pill)
                        .background(SurfaceDark)
                        .border(1.dp, DefaultAccentColor, TnShape.Pill)
                        .clickable { event.audioPath?.let(onPlayAudioClick) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play Voice Memo",
                        tint = DefaultAccentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlaying) "Pause" else "Play Voice Memo",
                        color = DefaultAccentColor,
                        fontSize = TnType.Caption,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val waypointChildren = childNotes.filter { it.parentWaypointId == event.id }
            if (waypointChildren.isNotEmpty()) {
                waypointChildren.forEach { child ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TnShape.Small)
                            .background(DefaultAccentColor.copy(alpha = 0.1f))
                            .border(1.dp, DefaultAccentColor.copy(alpha = 0.3f), TnShape.Small)
                            .clickable { onChildClick(child.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↳ Branched: ${child.title}",
                            color = DefaultAccentColor,
                            fontSize = TnType.Caption,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (event.type == EventType.NOTE) {
            IconButton(onClick = onBranchClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Branch Timer",
                    tint = TextSecondary
                )
            }
        }
    }
}
