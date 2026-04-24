package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.feature_history.domain.PastSession
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.mockFolders
import com.oblutack.timenote.feature_history.domain.mockSessions

@Composable
fun HistoryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        // Top Section (Mock Calendar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val days = listOf("Mon\n21", "Tue\n22", "Wed\n23", "Thu\n24", "Fri\n25")
            days.forEach { day ->
                val isSelected = day.startsWith("Wed")
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF2C2C2C) else Color.Transparent)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val parts = day.split("\n")
                    Text(parts[0], color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(parts[1], color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // "MY TIMENOTES" Section
        Text(
            text = "MY TIMENOTES",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(mockFolders) { folder ->
                FolderCard(folder)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // "RECENT SESSIONS" Section
        Text(
            text = "RECENT SESSIONS",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(mockSessions) { session ->
                SessionCard(session)
            }
        }
    }
}

@Composable
fun FolderCard(folder: TimenoteFolder) {
    Column(
        modifier = Modifier
            .size(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(folder.color)
        )

        Column {
            Text(
                text = folder.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${folder.sessionCount} sessions",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SessionCard(session: PastSession) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = session.duration,
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = session.description,
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            session.tags.forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, tag.color, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tag.name,
                        color = tag.color,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
