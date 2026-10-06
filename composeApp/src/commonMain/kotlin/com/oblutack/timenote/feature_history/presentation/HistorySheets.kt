package com.oblutack.timenote.feature_history.presentation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.setValue
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.OutlinedButton
import kotlinx.datetime.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Warning
import com.oblutack.timenote.feature_history.domain.DailySummary
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.text.style.TextAlign

// Bottom sheets used by HistoryScreen. State lives in HistoryScreen/HistoryViewModel;
// these composables only render and report user intent through callbacks.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditorSheet(
    isEditing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    customColors: List<Long>,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                Text(
                    text = if (!isEditing) "Create New Folder" else "Edit Folder",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = { Text("Folder name...", color = TextSecondary) },
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
                    value = description,
                    onValueChange = onDescriptionChange,
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
                                    if (color == selectedColor) {
                                        it.border(2.dp, Color.White, CircleShape)
                                    } else it
                                }
                                .clickable { onColorChange(color) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { if (name.isNotBlank()) onSave() },
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
fun FolderOptionsSheet(
    folder: ProjectFolder,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                Text(
                    text = folder.name,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onTogglePin()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.PushPin, contentDescription = if (folder.isPinned) "Unpin" else "Pin", tint = TextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = if (folder.isPinned) "Unpin Folder" else "Pin Folder", color = TextPrimary, fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDelete()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE53935))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Delete Folder", color = Color(0xFFE53935), fontSize = 16.sp)
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    current: SortOption,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp)) {
                Text("Sort By", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(16.dp))
                SortOption.entries.forEach { option ->
                    val isSelected = option == current
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option.displayName, color = if (isSelected) DefaultAccentColor else TextPrimary, fontSize = 16.sp)
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = DefaultAccentColor)
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterSheet(
    allTags: List<TimenoteFolder>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter by Tags", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onClear() }) { Text("Clear All", color = TextSecondary) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(allTags, key = { it.id }) { tag ->
                        val isSelected = selected.contains(tag.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggle(tag.id) }.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(tag.color))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(tag.name, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = isSelected, onCheckedChange = { onToggle(tag.id) },
                                colors = CheckboxDefaults.colors(checkedColor = DefaultAccentColor, uncheckedColor = TextSecondary)
                            )
                        }
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteBranchSheet(
    descendantCount: Int,
    onDeleteAll: () -> Unit,
    onDeleteOnly: () -> Unit,
    onCancel: () -> Unit
) {
        ModalBottomSheet(
            onDismissRequest = { onCancel() },
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color(0xFFE53935), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Delete Branch?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("This session has $descendantCount connected child sessions.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { onDeleteAll() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) { Text("Delete all ${descendantCount + 1} sessions", color = Color.White) }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { onDeleteOnly() },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                ) { Text("Delete just this session") }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { onCancel() }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySummarySheet(
    summary: DailySummary,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val topTag = summary.topTag

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp)) {

                // Formatted Date (e.g., "May 18, 2026")
                val monthStr = summary.date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                Text(
                    text = "$monthStr ${summary.date.dayOfMonth}, ${summary.date.year}",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Total Focused Time
                val h = summary.totalSeconds / 3600
                val m = (summary.totalSeconds % 3600) / 60
                val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"

                Text(
                    text = timeStr,
                    color = TextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Details Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sessions: ${summary.sessionCount}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)

                    if (topTag != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(TextSecondary.copy(alpha=0.3f)))
                        Spacer(modifier = Modifier.width(16.dp))

                        Text("Top Tag:", color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, topTag.color, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(topTag.name, color = topTag.color, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
}
