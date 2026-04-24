package com.oblutack.timenote.feature_timer.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.feature_timer.domain.mockTimelineEvents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen() {
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

        Text(
            text = "00:45:00",
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("What are you working on?", color = TextSecondary) },
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
            Button(
                onClick = {},
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

            OutlinedButton(
                onClick = {},
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
            onClick = {},
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
                itemsIndexed(mockTimelineEvents) { index, event ->
                    TimelineItem(
                        event = event,
                        isLastItem = index == mockTimelineEvents.size - 1
                    )
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

                // Outer circle (stroke)
                drawCircle(
                    color = DefaultAccentColor,
                    radius = circleRadius,
                    center = Offset(size.width / 2, circleCenterY),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Inner filled circle
                drawCircle(
                    color = DefaultAccentColor,
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
