package com.oblutack.timenote.feature_timer.presentation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
        ModalBottomSheet(
            onDismissRequest = { onAction(TimerAction.CloseAddNoteDialog) },
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                    val defaultColors = listOf(
                        Color(0xFF4FA8F9), Color(0xFF4CAF50), Color(0xFFFF9800),
                        Color(0xFF9C27B0), Color(0xFFE53935), Color(0xFF00BCD4)
                    )
                    val allColors = defaultColors + customColors.map { Color(it.toULong()) }

                    allColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, CircleShape)
                                .let {
                                    if (color == state.dialogNoteColor) {
                                        it.border(2.dp, Color.White, CircleShape)
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
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                    ) {
                        Text("Save Note")
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveTimenoteSheet(
    state: TimerState,
    onAction: (TimerAction) -> Unit
) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { onAction(TimerAction.SkipCategoriesAndSave) },
            sheetState = sheetState,
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Save Timenote", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) folder.color.copy(alpha = 0.2f) else SurfaceDark)
                                    .then(
                                        if (isSelected) Modifier.border(1.dp, folder.color, RoundedCornerShape(8.dp))
                                        else Modifier.border(1.dp, TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    )
                                    .clickable { onAction(TimerAction.SelectFolder(folder)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(folder.color, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(folder.name, color = TextPrimary, fontSize = 14.sp)
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) folder.color.copy(alpha = 0.2f) else Color.Transparent)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, folder.color, RoundedCornerShape(8.dp))
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
                                fontSize = 16.sp
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(1.dp, TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { onAction(TimerAction.OpenCreateTagDialog) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "+ New Tag",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(1.dp, TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { onAction(TimerAction.OpenManageTagsSheet) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Manage Tags",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save & Skip Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAction(TimerAction.SkipCategoriesAndSave) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip", color = TextSecondary)
                    }
                    Button(
                        onClick = { onAction(TimerAction.ConfirmCategoriesAndSave) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                    ) {
                        Text("Save")
                    }
                }
            }
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
                    shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp),
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
                    val defaultColors = listOf(
                        Color(0xFF4FA8F9), Color(0xFF4CAF50), Color(0xFFFF9800),
                        Color(0xFF9C27B0), Color(0xFFE53935), Color(0xFF00BCD4)
                    )
                    val allColors = defaultColors + customColors.map { Color(it.toULong()) }

                    allColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, CircleShape)
                                .let {
                                    if (color == state.newTagColor) {
                                        it.border(2.dp, Color.White, CircleShape)
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
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
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
                                            fontSize = 16.sp
                                        )
                                        val tagDescription = tag.description
                                        if (!tagDescription.isNullOrBlank()) {
                                            Text(
                                                text = tagDescription,
                                                color = TextSecondary.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
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
                                    IconButton(onClick = { onAction(TimerAction.DeleteTag(tag.id)) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE53935))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}
