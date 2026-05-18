package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextSecondary
import kotlinx.datetime.*

@Composable
fun FlowHeatmap(
    heatmapData: Map<String, Int>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    // FADE IN ANIMATION
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(600))
    }

    val today = Instant.fromEpochMilliseconds(com.oblutack.timenote.getCurrentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    // ISO Day: 1 = Monday, 7 = Sunday
    val todayIso = today.dayOfWeek.isoDayNumber

    val days = remember(today) {
        val list = mutableListOf<LocalDate?>()
        // Generate 15 full weeks. Pad the beginning so the top row is ALWAYS Monday.
        val totalPastDays = (14 * 7) + (todayIso - 1)
        for (i in totalPastDays downTo 1) {
            list.add(today.minus(i, DateTimeUnit.DAY))
        }
        list.add(today)
        // Pad the rest of the current week with nulls (future days)
        for (i in 1..(7 - todayIso)) {
            list.add(null)
        }
        list
    }

    val weeks = days.chunked(7)

    val monthLabels = remember(weeks) {
        val labels = mutableMapOf<Int, String>()
        var lastMonth = -1
        weeks.forEachIndexed { index, week ->
            // Find the first valid day in the week to check the month
            val firstValidDay = week.firstOrNull { it != null }
            if (firstValidDay != null && firstValidDay.monthNumber != lastMonth) {
                labels[index] = firstValidDay.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                lastMonth = firstValidDay.monthNumber
            }
        }
        labels
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp) // REDUCED OUTER PADDING
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .padding(12.dp) // REDUCED INNER PADDING
            .alpha(alpha.value) // APPLIES THE FADE ANIMATION
    ) {
        Text("FLOW STATE", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // --- Y-AXIS DAY LABELS ---
            Column(
                modifier = Modifier.padding(top = 22.dp, end = 8.dp), // Aligns perfectly under the Month labels
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val dayStyle = androidx.compose.ui.text.TextStyle(color = TextSecondary.copy(alpha=0.5f), fontSize = 10.sp)
                Text("M", style = dayStyle, modifier = Modifier.height(16.dp))
                Text("", style = dayStyle, modifier = Modifier.height(16.dp)) // Tue
                Text("W", style = dayStyle, modifier = Modifier.height(16.dp)) // Wed
                Text("", style = dayStyle, modifier = Modifier.height(16.dp)) // Thu
                Text("F", style = dayStyle, modifier = Modifier.height(16.dp)) // Fri
                Text("", style = dayStyle, modifier = Modifier.height(16.dp)) // Sat
                Text("", style = dayStyle, modifier = Modifier.height(16.dp)) // Sun
            }

            // --- THE HEATMAP GRID ---
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = weeks.size)
            ) {
                items(weeks.size) { weekIndex ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                        // Month Label
                        Box(modifier = Modifier.height(16.dp), contentAlignment = Alignment.BottomStart) {
                            if (monthLabels.containsKey(weekIndex)) {
                                Text(monthLabels[weekIndex]!!, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // 7 Day Squares
                        weeks[weekIndex].forEach { date ->
                            if (date == null) {
                                // Future day placeholder (invisible)
                                Box(modifier = Modifier.size(16.dp))
                            } else {
                                val seconds = heatmapData[date.toString()] ?: 0
                                val isSelected = selectedDate == date

                                val boxColor = when {
                                    seconds == 0 -> Color(0xFF2C2C2C)
                                    seconds < 3600 -> DefaultAccentColor.copy(alpha = 0.3f)
                                    seconds < 10800 -> DefaultAccentColor.copy(alpha = 0.6f)
                                    else -> DefaultAccentColor
                                }

                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(boxColor)
                                        .then(if (isSelected) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                                        .clickable { onDateSelected(date) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- THE LEGEND ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Less", color = TextSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2C2C2C)))
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(DefaultAccentColor.copy(alpha=0.3f)))
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(DefaultAccentColor.copy(alpha=0.6f)))
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(DefaultAccentColor))
            Spacer(modifier = Modifier.width(6.dp))
            Text("More", color = TextSecondary, fontSize = 10.sp)
        }
    }
}