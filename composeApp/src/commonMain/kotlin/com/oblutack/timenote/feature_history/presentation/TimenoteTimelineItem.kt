package com.oblutack.timenote.feature_history.presentation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalHapticFeedback
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder

@Composable
fun TimenoteTimelineItem(
    event: com.oblutack.timenote.feature_timer.domain.TimelineEvent,
    isLastItem: Boolean,
    useMonochrome: Boolean,
    playingAudioPath: String?,
    onPlayAudioClick: (String) -> Unit,
    onBranchClick: () -> Unit,
    childNotes: List<com.oblutack.timenote.feature_history.domain.Timenote> = emptyList(),
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 1. Determine size and color based on EventType
                val isStartOrEnd = event.type == EventType.START || event.type == EventType.END
                val circleRadius = if (isStartOrEnd) 7.dp.toPx() else 5.dp.toPx()
                val circleCenterY = 10.dp.toPx()

                val nodeColor = when (event.type) {
                    com.oblutack.timenote.feature_timer.domain.EventType.START -> if (useMonochrome) TextPrimary else Color(0xFF4CAF50)
                    com.oblutack.timenote.feature_timer.domain.EventType.END -> if (useMonochrome) TextSecondary else Color(0xFFE53935)
                    else -> event.color ?: DefaultAccentColor
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
                        color = SurfaceDark,
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
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.timestamp,
                color = TextSecondary,
                fontSize = 14.sp
            )

            if (event.audioPath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isPlaying = playingAudioPath == event.audioPath
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceDark)
                        .border(1.dp, DefaultAccentColor, RoundedCornerShape(50))
                        .clickable { onPlayAudioClick(event.audioPath!!) }
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
                        fontSize = 12.sp,
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(DefaultAccentColor.copy(alpha = 0.1f))
                            .border(1.dp, DefaultAccentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onChildClick(child.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↳ Branched: ${child.title}",
                            color = DefaultAccentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (event.type == EventType.NOTE) {
            IconButton(onClick = onBranchClick) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = "Branch Timer",
                    tint = TextSecondary
                )
            }
        }
    }
}
