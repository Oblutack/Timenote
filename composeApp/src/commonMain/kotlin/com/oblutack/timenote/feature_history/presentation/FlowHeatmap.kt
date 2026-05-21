package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import kotlinx.datetime.*
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents

@Composable
fun FlowHeatmap(
    heatmapData: Map<String, Int>,
    selectedDate: LocalDate?,
    streaks: Pair<Int, Int>,
    onDateSelected: (LocalDate) -> Unit
) {
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(600))
    }

    val today = Instant.fromEpochMilliseconds(com.oblutack.timenote.getCurrentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val todayIso = today.dayOfWeek.isoDayNumber

    val days = remember(today) {
        val list = mutableListOf<LocalDate?>()
        val totalPastDays = (14 * 7) + (todayIso - 1)
        for (i in totalPastDays downTo 1) {
            list.add(today.minus(DatePeriod(days = i)))
        }
        list.add(today)
        for (i in 1..(7 - todayIso)) {
            list.add(null)
        }
        list
    }

    val weeks = days.chunked(7)

    val monthLabels = remember(weeks) {
        val labels = mutableMapOf<Int, String>()
        var lastMonth: Month? = null
        weeks.forEachIndexed { index, week ->
            val firstValidDay = week.firstOrNull { it != null }
            if (firstValidDay != null && firstValidDay.month != lastMonth) {
                labels[index] = firstValidDay.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                lastMonth = firstValidDay.month
            }
        }
        labels
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .alpha(alpha.value)
    ) {
        // --- TOP BAR (PREMIUM ICONS INSTEAD OF EMOJIS) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FLOW STATE", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Minimalist Blue Circle for Current Streak
                Box(modifier = Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(DefaultAccentColor))
                Spacer(Modifier.width(6.dp))
                Text("Current: ${streaks.first}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.width(16.dp))

                // Minimalist Orange Circle for Best Streak
                Box(modifier = Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFF9800)))
                Spacer(Modifier.width(6.dp))
                Text("Best: ${streaks.second}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- RESTORED HEATMAP GRID ---
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(top = 22.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val dayStyle = androidx.compose.ui.text.TextStyle(color = TextSecondary.copy(alpha=0.5f), fontSize = 10.sp)
                Text("M", style = dayStyle, modifier = Modifier.height(16.dp))
                Text("", style = dayStyle, modifier = Modifier.height(16.dp))
                Text("W", style = dayStyle, modifier = Modifier.height(16.dp))
                Text("", style = dayStyle, modifier = Modifier.height(16.dp))
                Text("F", style = dayStyle, modifier = Modifier.height(16.dp))
                Text("", style = dayStyle, modifier = Modifier.height(16.dp))
                Text("", style = dayStyle, modifier = Modifier.height(16.dp))
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = weeks.size)
            ) {
                items(weeks.size) { weekIndex ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.height(16.dp), contentAlignment = Alignment.BottomStart) {
                            if (monthLabels.containsKey(weekIndex)) {
                                Text(monthLabels[weekIndex]!!, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        weeks[weekIndex].forEach { date ->
                            if (date == null) {
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