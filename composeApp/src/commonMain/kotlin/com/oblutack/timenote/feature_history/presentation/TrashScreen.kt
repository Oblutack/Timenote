package com.oblutack.timenote.feature_history.presentation

import com.oblutack.timenote.ui.components.EmptyState
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oblutack.timenote.core.TRASH_RETENTION_DAYS
import com.oblutack.timenote.core.describeTrashItem
import com.oblutack.timenote.getCurrentTimeMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oblutack.timenote.ui.theme.BackgroundDark
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.di.trashViewModel

@Composable
fun TrashScreen(
    onBackClick: () -> Unit,
    viewModel: TrashViewModel = trashViewModel()
) {
    val deletedFolders by viewModel.deletedFolders.collectAsState()
    val deletedTimenotes by viewModel.deletedTimenotes.collectAsState()
    val now = getCurrentTimeMillis()
    var confirmEmpty by remember { mutableStateOf(false) }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = SurfaceDark,
            title = { Text("Empty the trash?", color = TextPrimary) },
            text = { Text("Everything in it is removed for good, also from your Google Drive copy if sync is on. This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmEmpty = false; viewModel.emptyTrash() }) { Text("Empty trash", color = TnTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

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
                Text("Recently Deleted", color = TextPrimary, fontSize = TnType.Title, fontWeight = FontWeight.Bold)
            }

            if (deletedFolders.isNotEmpty() || deletedTimenotes.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) {
                    Text("Empty Trash", color = TnTheme.colors.danger, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (deletedFolders.isNotEmpty() || deletedTimenotes.isNotEmpty()) {
            Text(
                text = "Deleted sessions and folders are removed for good after $TRASH_RETENTION_DAYS days.",
                color = TextSecondary,
                fontSize = TnType.Label,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (deletedFolders.isEmpty() && deletedTimenotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Delete,
                    title = "Trash is empty",
                    hint = "Deleted sessions and folders stay here for 30 days, then they are removed for good."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (deletedFolders.isNotEmpty()) {
                    item {
                        Text("Folders", color = TextSecondary, fontSize = TnType.Body, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(deletedFolders, key = { "folder_${it.id}" }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(TnShape.Card)
                                .background(SurfaceDark)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = folder.name, color = TextPrimary, fontSize = TnType.BodyLarge, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Folder · ${describeTrashItem(now, folder.deletedAt)}", color = TextSecondary, fontSize = TnType.Caption)
                            }
                            Row {
                                IconButton(onClick = { viewModel.restoreFolder(folder.id) }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restore", tint = TextPrimary)
                                }
                                IconButton(onClick = { viewModel.hardDeleteFolder(folder.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Permanently", tint = TnTheme.colors.danger)
                                }
                            }
                        }
                    }
                }

                if (deletedTimenotes.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Sessions", color = TextSecondary, fontSize = TnType.Body, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(deletedTimenotes, key = { "note_${it.id}" }) { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(TnShape.Card)
                                .background(SurfaceDark)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = note.title, color = TextPrimary, fontSize = TnType.BodyLarge, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "${note.duration} · ${describeTrashItem(now, note.deletedAt)}", color = TextSecondary, fontSize = TnType.Caption)
                            }
                            Row {
                                IconButton(onClick = { viewModel.restoreTimenote(note.id) }) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restore", tint = TextPrimary)
                                }
                                IconButton(onClick = { viewModel.hardDeleteTimenote(note.id) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Permanently", tint = TnTheme.colors.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

