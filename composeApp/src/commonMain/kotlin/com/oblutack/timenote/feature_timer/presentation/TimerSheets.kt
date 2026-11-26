package com.oblutack.timenote.feature_timer.presentation
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TagColorChoices
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.ui.theme.BackgroundDark
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow

// Bottom sheets used by TimerScreen. Everything is driven by TimerState and reported back
// through onAction, so these composables hold no state of their own.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteSheet(
    state: TimerState,
    customColors: List<Long>,
    onAction: (TimerAction) -> Unit
) {
        val noteFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { noteFocus.requestFocus() } // ready to type as soon as the sheet opens

        ModalBottomSheet(
            onDismissRequest = { onAction(TimerAction.DismissAddNoteDialog) },
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                Text("Add Note", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.dialogNoteText,
                    onValueChange = { onAction(TimerAction.UpdateDialogNoteText(it)) },
                    placeholder = { Text("Write note here...", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth().focusRequester(noteFocus),
                    shape = TnShape.Medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val defaultColors = TagColorChoices
                    val allColors = defaultColors + customColors.map { Color(it.toULong()) }

                    allColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, CircleShape)
                                .let {
                                    if (color == state.dialogNoteColor) {
                                        it.border(2.dp, TnTheme.colors.onBackground, CircleShape)
                                    } else it
                                }
                                .clickable { onAction(TimerAction.UpdateDialogNoteColor(color)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAction(TimerAction.CloseAddNoteDialog) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { onAction(TimerAction.SaveNote) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = TnTheme.colors.onAccent)
                    ) {
                        Text("Save Note")
                    }
                }
            }
        }
}

/** One number of the summary: a caption over a value. */
@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = TextSecondary, fontSize = TnType.Caption)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = TextPrimary, fontSize = TnType.Subtitle, fontFamily = TnTheme.mono, fontWeight = FontWeight.Medium)
    }
}

/**
 * Shown every time a session ends. Nothing is saved until here is left: Save saves, closing the sheet saves too (a swipe
 * must never lose a session), "Keep going" takes the end back and "Discard session" throws the session away (after asking).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummarySheet(
    state: TimerState,
    summary: SessionSummary,
    onAction: (TimerAction) -> Unit
) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var menuOpen by remember { mutableStateOf(false) }
        var confirmDiscard by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { onAction(TimerAction.SaveSession) },
            sheetState = sheetState,
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Session summary", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = SurfaceDark) {
                            DropdownMenuItem(
                                text = { Text("Keep going", color = TextPrimary) },
                                onClick = { menuOpen = false; onAction(TimerAction.KeepGoing) }
                            )
                            DropdownMenuItem(
                                text = { Text("Discard session", color = TnTheme.colors.danger) },
                                onClick = { menuOpen = false; confirmDiscard = true }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.sessionTitle,
                    onValueChange = { onAction(TimerAction.UpdateSessionTitle(it)) },
                    placeholder = { Text("Untitled Session", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TnShape.Medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryStat("Total", com.oblutack.timenote.core.formatDuration(summary.totalSeconds), Modifier.weight(1f))
                    SummaryStat("Focus", com.oblutack.timenote.core.formatDuration(summary.activeSeconds), Modifier.weight(1f))
                    SummaryStat("Breaks", com.oblutack.timenote.core.formatDuration(summary.pauseSeconds), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (summary.noteCount == 1) "1 note" else "${summary.noteCount} notes",
                    color = TextSecondary,
                    fontSize = TnType.Body
                )
                Spacer(modifier = Modifier.height(24.dp))

                // --- NEW: FOLDER SELECTION ---
                if (state.availableFolders.isNotEmpty()) {
                    Text("Select a folder (Optional):", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.availableFolders) { folder ->
                            val isSelected = state.selectedFolder?.id == folder.id
                            Box(
                                modifier = Modifier
                                    .clip(TnShape.Small)
                                    .background(if (isSelected) folder.color.copy(alpha = 0.2f) else SurfaceDark)
                                    .then(
                                        if (isSelected) Modifier.border(1.dp, folder.color, TnShape.Small)
                                        else Modifier.border(1.dp, TextSecondary.copy(alpha = 0.3f), TnShape.Small)
                                    )
                                    .clickable { onAction(TimerAction.SelectFolder(folder)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(folder.color, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(folder.name, color = TextPrimary, fontSize = TnType.Body)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                // -----------------------------

                // Changed the text to clarify Folders vs Tags
                Text("Select tags (Optional):", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // Existing Tags List
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.availableTags.forEach { folder ->
                        val isSelected = state.selectedCategories.any { it.id == folder.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(TnShape.Small)
                                .background(if (isSelected) folder.color.copy(alpha = 0.2f) else Color.Transparent)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, folder.color, TnShape.Small)
                                    else Modifier
                                )
                                .clickable { onAction(TimerAction.ToggleCategory(folder)) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(folder.color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                color = TextPrimary,
                                fontSize = TnType.BodyLarge
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(TnShape.Small)
                                .background(SurfaceDark)
                                .border(1.dp, TextSecondary.copy(alpha = 0.5f), TnShape.Small)
                                .clickable { onAction(TimerAction.OpenCreateTagDialog) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "+ New Tag",
                                color = TextSecondary,
                                fontSize = TnType.BodyLarge
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(TnShape.Small)
                                .background(SurfaceDark)
                                .border(1.dp, TextSecondary.copy(alpha = 0.5f), TnShape.Small)
                                .clickable { onAction(TimerAction.OpenManageTagsSheet) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Manage Tags",
                                color = TextSecondary,
                                fontSize = TnType.BodyLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onAction(TimerAction.SaveSession) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = TnTheme.colors.onAccent)
                ) {
                    Text("Save")
                }
            }
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                containerColor = SurfaceDark,
                title = { Text("Discard this session?", color = TextPrimary) },
                text = {
                    Text(
                        "Its time and ${if (summary.noteCount == 1) "1 note" else "${summary.noteCount} notes"} will be thrown away. This cannot be undone.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { confirmDiscard = false; onAction(TimerAction.DiscardSession) }) {
                        Text("Discard", color = TnTheme.colors.danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text("Cancel", color = TextSecondary) }
                }
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTagSheet(
    state: TimerState,
    customColors: List<Long>,
    onAction: (TimerAction) -> Unit
) {
        ModalBottomSheet(
            onDismissRequest = { onAction(TimerAction.CloseCreateTagDialog) },
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                Text(if (state.tagBeingEditedId == null) "Create New Tag" else "Edit Tag", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.newTagName,
                    onValueChange = { onAction(TimerAction.UpdateNewTagName(it)) },
                    placeholder = { Text("Tag name...", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = TnShape.Medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.newTagDescription,
                    onValueChange = { onAction(TimerAction.UpdateNewTagDescription(it)) },
                    placeholder = { Text("Description (Optional)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    shape = TnShape.Medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val defaultColors = TagColorChoices
                    val allColors = defaultColors + customColors.map { Color(it.toULong()) }

                    allColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, CircleShape)
                                .let {
                                    if (color == state.newTagColor) {
                                        it.border(2.dp, TnTheme.colors.onBackground, CircleShape)
                                    } else it
                                }
                                .clickable { onAction(TimerAction.UpdateNewTagColor(color)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAction(TimerAction.CloseCreateTagDialog) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { onAction(TimerAction.SaveNewTag) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = TnTheme.colors.onAccent)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTagsSheet(
    state: TimerState,
    onAction: (TimerAction) -> Unit
) {
        var tagToDelete by remember { mutableStateOf<com.oblutack.timenote.feature_history.domain.TimenoteFolder?>(null) }
        tagToDelete?.let { tag ->
            AlertDialog(
                onDismissRequest = { tagToDelete = null },
                containerColor = SurfaceDark,
                title = { Text("Delete \"${tag.name}\"?", color = TextPrimary) },
                text = { Text("This tag is removed from your list of tags.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { onAction(TimerAction.DeleteTag(tag.id)); tagToDelete = null }) {
                        Text("Delete", color = TnTheme.colors.danger)
                    }
                },
                dismissButton = { TextButton(onClick = { tagToDelete = null }) { Text("Cancel", color = TextSecondary) } }
            )
        }

        ModalBottomSheet(
            onDismissRequest = { onAction(TimerAction.CloseManageTagsSheet) },
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                Text(
                    text = "Manage Tags",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (state.availableTags.isEmpty()) {
                    Text(
                        text = "No custom tags created yet.",
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(state.availableTags) { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(tag.color, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = tag.name,
                                            color = TextPrimary,
                                            fontSize = TnType.BodyLarge
                                        )
                                        val tagDescription = tag.description
                                        if (!tagDescription.isNullOrBlank()) {
                                            Text(
                                                text = tagDescription,
                                                color = TextSecondary.copy(alpha = 0.7f),
                                                fontSize = TnType.Caption,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Row {
                                    IconButton(onClick = { onAction(TimerAction.EditTag(tag)) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
                                    }
                                    IconButton(onClick = { tagToDelete = tag }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TnTheme.colors.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}
