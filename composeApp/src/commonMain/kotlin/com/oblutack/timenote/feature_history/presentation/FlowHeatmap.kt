package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextSecondary
import kotlinx.datetime.*
import androidx.compose.foundation.border

@Composable
fun FlowHeatmap(
    heatmapData: Map<String, Int>, // "YYYY-MM-DD" -> Total Seconds
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    // Generate the last 100 days (Roughly 14 weeks / columns)
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val daysToShow = 100

    val days = remember(today) {
        val list = mutableListOf<LocalDate>()
        for (i in (daysToShow - 1) downTo 0) {
            list.add(today.minus(i, DateTimeUnit.DAY))
        }
        list
    }

    // Group the days into columns of 7 (Weeks)
    val weeks = days.chunked(7)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text("FLOW STATE", color = TextSecondary, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            // Scroll to the very end (today) automatically
            state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = weeks.size)
        ) {
            items(weeks.size) { weekIndex ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    weeks[weekIndex].forEach { date ->
                        val dateStr = date.toString()
                        val seconds = heatmapData[dateStr] ?: 0
                        val isSelected = selectedDate == date

                        // Calculate Color Intensity based on hours focused!
                        val boxColor = when {
                            seconds == 0 -> SurfaceDark
                            seconds < 3600 -> DefaultAccentColor.copy(alpha = 0.3f) // Less than 1 hr
                            seconds < 10800 -> DefaultAccentColor.copy(alpha = 0.6f) // 1-3 hrs
                            else -> DefaultAccentColor // 3+ hrs (Max Intensity!)
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(boxColor)
                                // If selected, draw a white border around it
                                .then(if (isSelected) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                                .clickable { onDateSelected(date) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // The Legend
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Less", color = TextSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceDark))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(DefaultAccentColor.copy(alpha=0.3f)))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(DefaultAccentColor.copy(alpha=0.6f)))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(DefaultAccentColor))
            Spacer(modifier = Modifier.width(4.dp))
            Text("More", color = TextSecondary, fontSize = 10.sp)
        }
    }
}