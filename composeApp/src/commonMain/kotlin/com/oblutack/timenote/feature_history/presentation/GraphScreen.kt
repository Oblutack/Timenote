package com.oblutack.timenote.feature_history.presentation

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.Timenote
import kotlin.math.sqrt
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape


// Holds the calculated X,Y positions for the Canvas to draw
data class GraphNode(
    val note: Timenote,
    val x: Float,
    val y: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    onBackClick: () -> Unit,
    onTimenoteClick: (String) -> Unit,
    viewModel: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel { HistoryViewModel() }
) {
    val timenotes by SessionRepository.timenotes.collectAsState()
    val selectedGraphNodeId by viewModel.selectedGraphNodeId.collectAsState()

    // Interactive Canvas State (Pan & Zoom)
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset(200f, 200f)) }
    val textMeasurer = rememberTextMeasurer()

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "NodePulse")

    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = 55f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "RadiusPulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    val enableBlur by com.oblutack.timenote.data.repository.SettingsRepository.enableBackgroundBlurFlow.collectAsState(initial = true)

    val blurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (enableBlur && selectedGraphNodeId != null) 16.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "GraphBlur"
    )

    // --- THE UPGRADED AUTO-LAYOUT ALGORITHM ---
    val nodes = remember(timenotes) {
        val calculatedNodes = mutableListOf<GraphNode>()
        val childrenMap = timenotes.groupBy { it.parentTimenoteId }
        val roots = timenotes.filter { it.parentTimenoteId == null || timenotes.none { parent -> parent.id == it.parentTimenoteId } }

        var currentY = 400f

        // FIX: Returns Float so the parent can calculate the exact mathematical center of its children!
        fun placeNode(note: Timenote, depth: Int): Float {
            val x = 300f + (depth * 500f)

            val children = childrenMap[note.id] ?: emptyList()
            val y = if (children.isNotEmpty()) {
                val childYs = children.map { placeNode(it, depth + 1) }
                childYs.average().toFloat() // Centers the parent!
            } else {
                val myY = currentY
                currentY += 250f
                myY
            }

            calculatedNodes.add(GraphNode(note, x, y))
            return y
        }

        roots.forEach { root -> placeNode(root, 0) }
        calculatedNodes
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark).blur(radius = blurRadius)) {
        // --- TOP BAR ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Knowledge Graph", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // --- THE CANVAS ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        scale = (scale * zoomChange).coerceIn(0.2f, 3f)
                        pan += panChange
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val canvasTapX = (tapOffset.x - pan.x) / scale
                            val canvasTapY = (tapOffset.y - pan.y) / scale

                            nodes.forEach { node ->
                                val dx = canvasTapX - node.x
                                val dy = canvasTapY - node.y
                                val distance = sqrt((dx * dx + dy * dy).toDouble())
                                if (distance < 60f) {
                                    viewModel.selectGraphNode(node.note.id)
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(pan.x, pan.y)
                    scale(scale, scale, Offset.Zero)
                }) {
                    // 1. PREMIUM DOT GRID BACKGROUND
                    val dotSpacing = 100f
                    for (x in -2000..2000 step dotSpacing.toInt()) {
                        for (y in -2000..2000 step dotSpacing.toInt()) {
                            drawCircle(
                                color = TextSecondary.copy(alpha = 0.1f),
                                radius = 2f,
                                center = Offset(x.toFloat(), y.toFloat())
                            )
                        }
                    }

                    // 2. DRAW CONNECTIONS (Bézier Curves for Children)
                    nodes.forEach { node ->
                        if (node.note.parentTimenoteId != null) {
                            val parentNode = nodes.find { it.note.id == node.note.parentTimenoteId }
                            if (parentNode != null) {
                                val path = Path().apply {
                                    moveTo(parentNode.x, parentNode.y)
                                    cubicTo(
                                        x1 = parentNode.x + 200f, y1 = parentNode.y,
                                        x2 = node.x - 200f, y2 = node.y,
                                        x3 = node.x, y3 = node.y
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = DefaultAccentColor.copy(alpha = 0.5f),
                                    style = Stroke(width = 4f)
                                )
                            }
                        }
                    }

                    // 3. DRAW MENTION LINKS (Dashed Purple Lines for Tags)
                    // FIX: Moved inside `withTransform` so it pans/zooms with the graph!
                    val mentionRegex = Regex("@\\[.*?\\]\\((.*?)\\)")
                    nodes.forEach { node ->
                        val mentions = mentionRegex.findAll(node.note.description).map { it.groupValues[1] }.toList()
                        mentions.forEach { targetId ->
                            val targetNode = nodes.find { it.note.id == targetId }
                            if (targetNode != null) {
                                val path = Path().apply {
                                    moveTo(node.x, node.y)
                                    lineTo(targetNode.x, targetNode.y)
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF9C27B0).copy(alpha = 0.5f),
                                    style = Stroke(
                                        width = 3f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                                    )
                                )
                            }
                        }
                    }

                    // 4. DRAW NODES (Glowing Orbs + Titles)
                    nodes.forEach { node ->
                        val nodeColor = node.note.tags.firstOrNull()?.color ?: DefaultAccentColor

                        drawCircle(color = nodeColor.copy(alpha = glowAlpha), radius = glowRadius, center = Offset(node.x, node.y))
                        drawCircle(color = nodeColor, radius = 18f, center = Offset(node.x, node.y))
                        drawCircle(color = BackgroundDark, radius = 8f, center = Offset(node.x, node.y))

                        val title = node.note.title.ifBlank { "Untitled" }
                        val textLayoutResult = textMeasurer.measure(
                            text = title,
                            style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        )

                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = node.x - (textLayoutResult.size.width / 2),
                                y = node.y + 35f
                            )
                        )
                    }
                }
            }
        }
    }

    if (selectedGraphNodeId != null) {
        val note = timenotes.find { it.id == selectedGraphNodeId }
        if (note != null) {
            val childCount = SessionRepository.getDescendantIds(note.id).size
            val familyTime = viewModel.calculateFamilyTime(note.id)

            ModalBottomSheet(
                onDismissRequest = { viewModel.selectGraphNode(null) },
                containerColor = SurfaceDark
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Text(
                        text = note.title.ifBlank { "Untitled" },
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val firstTag = note.tags.firstOrNull()
                    if (firstTag != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .background(firstTag.color.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(firstTag.name, color = firstTag.color, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.3f)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, TextSecondary.copy(alpha=0.3f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {

                                // ROW 1: Total Times
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Node Time", color = TextSecondary, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(note.duration, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Family Time ($childCount children)", color = TextSecondary, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(familyTime, color = DefaultAccentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TextSecondary.copy(alpha = 0.15f)))
                                Spacer(modifier = Modifier.height(16.dp))

                                // ROW 2: Work vs Pause Breakdown
                                val totalSeconds = note.activeSeconds + note.pauseSeconds
                                val workRatio = if (totalSeconds > 0) note.activeSeconds.toFloat() / totalSeconds.toFloat() else 1f
                                val workPercent = if (totalSeconds > 0) kotlin.math.round(workRatio * 100).toInt() else 100
                                val pausePercent = if (totalSeconds > 0) 100 - workPercent else 0
                                val tagColor = note.tags.firstOrNull()?.color ?: DefaultAccentColor

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tagColor))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("$workPercent% Work", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF333333)))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("$pausePercent% Pause", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // The actual Progress Bar
                                Row(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50))) {
                                    Box(modifier = Modifier.weight(workRatio.coerceAtLeast(0.01f)).fillMaxHeight().background(tagColor))
                                    Box(modifier = Modifier.weight((1f - workRatio).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF333333)))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.selectGraphNode(null)
                            onTimenoteClick(note.id)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor)
                    ) {
                        Text("View Details", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}