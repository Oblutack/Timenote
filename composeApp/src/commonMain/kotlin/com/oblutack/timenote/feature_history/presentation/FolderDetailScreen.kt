package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.data.repository.SessionRepository

@Composable
fun FolderDetailScreen(
    folderId: String,
    onBackClick: () -> Unit,
    onTimenoteClick: (String) -> Unit
) {
    val allTimenotes by SessionRepository.timenotes.collectAsState()
    val folderTimenotes = allTimenotes.filter { it.folderId == folderId }
    val folder = SessionRepository.folders.value.find { it.id == folderId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(top = 16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary
                )
            }
            Text(
                text = "Folder Details",
                color = TextSecondary,
                fontSize = 16.sp
            )
        }

        if (folder != null) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = folder.name,
                    color = folder.color,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${folderTimenotes.size} sessions",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }

            // List or Empty State
            if (folderTimenotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sessions in this folder yet.",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(folderTimenotes, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            onClick = { onTimenoteClick(session.id) }
                        )
                    }
                }
            }
        } else {
            // Handle folder not found gracefully if it was deleted
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Folder not found.",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        }
    }
}

