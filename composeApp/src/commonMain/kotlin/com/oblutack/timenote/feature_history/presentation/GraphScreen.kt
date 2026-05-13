package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.data.repository.SessionRepository
import kotlin.math.sqrt

@Composable
fun GraphScreen(onBackClick: () -> Unit, onTimenoteClick: (String) -> Unit) {
    val timenotes by SessionRepository.timenotes.collectAsState()
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Knowledge Graph",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Graph Canvas Box
        Box(modifier = Modifier.fillMaxSize()) {

            // Calculate Math (Node Placement)
            val nodePositions = remember(timenotes) {
                val positions = mutableMapOf<String, Offset>()
                val depths = mutableMapOf<String, Int>()

                fun getDepth(id: String): Int {
                    if (depths.containsKey(id)) return depths[id]!!
                    val node = timenotes.find { it.id == id } ?: return 0
                    if (node.parentTimenoteId == null) {
                        depths[id] = 0
                        return 0
                    }
                    val currentDepth = getDepth(node.parentTimenoteId) + 1
                    depths[id] = currentDepth
                    return currentDepth
                }

                timenotes.forEachIndexed { index, node ->
                    val x = 100f + getDepth(node.id) * 300f
                    val y = (index * 150f) + 100f
                    positions[node.id] = Offset(x, y)
                }
                positions
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            // Interaction: Detect clicks on nodes
                            for ((id, pos) in nodePositions) {
                                val dx = tapOffset.x - pos.x
                                val dy = tapOffset.y - pos.y
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist < 30.dp.toPx()) {
                                    onTimenoteClick(id)
                                    break // Trigger for first matching node within hit target
                                }
                            }
                        }
                    }
            ) {
                // 1. Draw Lines First (underneath nodes)
                timenotes.forEach { child ->
                    val parentId = child.parentTimenoteId
                    if (parentId != null) {
                        val pOffset = nodePositions[parentId]
                        val cOffset = nodePositions[child.id]
                        if (pOffset != null && cOffset != null) {
                            drawLine(
                                color = TextSecondary.copy(alpha = 0.3f),
                                start = pOffset,
                                end = cOffset,
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }

                // 2. Draw Nodes Second (on top of lines)
                timenotes.forEach { node ->
                    val pos = nodePositions[node.id]
                    if (pos != null) {
                        // Node Circle
                        val color = node.tags.firstOrNull()?.color ?: DefaultAccentColor
                        drawCircle(
                            color = color,
                            radius = 16.dp.toPx(),
                            center = pos
                        )

                        // Node Title Text
                        val textLayoutResult = textMeasurer.measure(
                            text = node.title,
                            style = TextStyle(color = TextPrimary, fontSize = 12.sp)
                        )

                        // Center the text horizontally under the node
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = pos.x - (textLayoutResult.size.width / 2f),
                                y = pos.y + 24.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
    }
}

