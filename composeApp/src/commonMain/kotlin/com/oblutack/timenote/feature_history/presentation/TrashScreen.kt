package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary

@Composable
fun TrashScreen(
    onBackClick: () -> Unit,
    viewModel: TrashViewModel = viewModel { TrashViewModel() }
) {
    val deletedFolders by viewModel.deletedFolders.collectAsState()
    val deletedTimenotes by viewModel.deletedTimenotes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recently Deleted", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            if (deletedFolders.isNotEmpty() || deletedTimenotes.isNotEmpty()) {
                TextButton(onClick = { viewModel.emptyTrash() }) {
                    Text("Empty Trash", color = Color(0xFFE53935), fontWeight = FontWeight.Medium)
                }
            }
        }

        if (deletedFolders.isEmpty() && deletedTimenotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Trash is empty.", color = TextSecondary, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (deletedFolders.isNotEmpty()) {
                    item {
                        Text("Folders", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(deletedFolders, key = { "folder_${it.id}" }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceDark)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = folder.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Folder", color = TextSecondary, fontSize = 12.sp)
                            }
                            Row {
                                IconButton(onClick = { viewModel.restoreFolder(folder.id) }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restore", tint = TextPrimary)
                                }
                                IconButton(onClick = { viewModel.hardDeleteFolder(folder.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Permanently", tint = Color(0xFFE53935))
                                }
                            }
                        }
                    }
                }

                if (deletedTimenotes.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Sessions", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(deletedTimenotes, key = { "note_${it.id}" }) { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceDark)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = note.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = note.duration, color = TextSecondary, fontSize = 12.sp)
                            }
                            Row {
                                IconButton(onClick = { viewModel.restoreTimenote(note.id) }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restore", tint = TextPrimary)
                                }
                                IconButton(onClick = { viewModel.hardDeleteTimenote(note.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Permanently", tint = Color(0xFFE53935))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

