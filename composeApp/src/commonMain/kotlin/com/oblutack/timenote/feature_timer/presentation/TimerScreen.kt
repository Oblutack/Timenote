package com.oblutack.timenote.feature_timer.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel = viewModel { TimerViewModel() }
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Timenote",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(48.dp))

        // --- NEW: Smooth Fade-In "Last Session" Label ---
        // 1. Determine if we should show it
        val showLastLabel = !state.isRunning && !state.isPaused && state.timelineEvents.isNotEmpty()

        // 2. Animate the opacity (alpha) from 0f (invisible) to 1f (fully visible)
        val labelAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (showLastLabel) 1f else 0f,
            label = "fadeAnimation"
        )

        // 3. Always draw the text to reserve the space, but apply the animated alpha!
        Text(
            text = "YOUR LAST TIMENOTE",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.alpha(labelAlpha)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.displayTime,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = state.sessionTitle,
            onValueChange = { viewModel.onAction(TimerAction.UpdateSessionTitle(it)) },
            placeholder = { Text("Session Title", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!state.isRunning) {
                Button(
                    onClick = { viewModel.onAction(TimerAction.Start) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DefaultAccentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                    Spacer(Modifier.width(8.dp))
                    Text("Start")
                }
            } else if (!state.isPaused) {
                Button(
                    onClick = { viewModel.onAction(TimerAction.Pause) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DefaultAccentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                    Spacer(Modifier.width(8.dp))
                    Text("Pause")
                }
            } else {
                Button(
                    onClick = { viewModel.onAction(TimerAction.Resume) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DefaultAccentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    Spacer(Modifier.width(8.dp))
                    Text("Resume")
                }
            }

            OutlinedButton(
                onClick = { viewModel.onAction(TimerAction.End) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                    containerColor = SurfaceDark
                ),
                border = null,
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("End")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { viewModel.onAction(TimerAction.OpenAddNoteDialog) },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextSecondary
            ),
            border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
        ) {
            Text("+ Add Note")
        }

        Spacer(modifier = Modifier.height(32.dp))


        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "TIMELINE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(state.timelineEvents) { index, event ->
                    TimelineItem(
                        event = event,
                        isLastItem = index == state.timelineEvents.size - 1
                    )
                }
            }
        }
    }

    if (state.isAddNoteDialogOpen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.onAction(TimerAction.CloseAddNoteDialog) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("Add Note", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.dialogNoteText,
                    onValueChange = { viewModel.onAction(TimerAction.UpdateDialogNoteText(it)) },
                    placeholder = { Text("Write note here...", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf(
                        Color(0xFF4FA8F9), Color(0xFF4CAF50),
                        Color(0xFFFF9800), Color(0xFF9C27B0)
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, androidx.compose.foundation.shape.CircleShape)
                                .let {
                                    if (color == state.dialogNoteColor) {
                                        it.border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                    } else it
                                }
                                .clickable { viewModel.onAction(TimerAction.UpdateDialogNoteColor(color)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.onAction(TimerAction.CloseAddNoteDialog) }) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { viewModel.onAction(TimerAction.SaveNote) },
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                    ) {
                        Text("Save Note")
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItem(event: TimelineEvent, isLastItem: Boolean) {
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
                val circleRadius = 5.dp.toPx()
                val circleCenterY = 10.dp.toPx()
                val nodeColor = event.color ?: DefaultAccentColor

                // Outer circle (stroke)
                drawCircle(
                    color = nodeColor,
                    radius = circleRadius,
                    center = Offset(size.width / 2, circleCenterY),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Inner filled circle
                drawCircle(
                    color = nodeColor,
                    radius = circleRadius * 0.5f,
                    center = Offset(size.width / 2, circleCenterY)
                )

                // Vertical line connecting nodes
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
