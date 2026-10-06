package com.oblutack.timenote.feature_history.presentation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalHapticFeedback
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder

// Sections and bottom sheets used by TimenoteDetailScreen. State lives in the screen/ViewModel;
// these composables only render and report user intent through callbacks.

@Composable
fun VoiceNotesSection(
    voiceNotes: List<String>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    playingAudioPath: String?,
    isRecording: Boolean,
    enableHaptics: Boolean,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. Determine what to show
        val hasManyNotes = voiceNotes.size > 3
        val displayedNotes = if (isExpanded) voiceNotes else voiceNotes.take(3)

        // 2. Render the visible notes
        displayedNotes.forEachIndexed { index, path ->
            val absoluteIndex = voiceNotes.indexOf(path).takeIf { it != -1 }?.plus(1) ?: (index + 1)
            val isPlaying = playingAudioPath == path

            Row(
                modifier = Modifier
                    // REMOVED: .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceDark)
                    .border(1.dp, DefaultAccentColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable { onPlay(path) }
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), // Tighter padding for a sleek pill
                verticalAlignment = Alignment.CenterVertically
                // REMOVED: horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = if (isPlaying) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = DefaultAccentColor,
                    modifier = Modifier.size(20.dp) // Slightly smaller icon
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Voice Note $absoluteIndex",
                    color = DefaultAccentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))

                // The Trash Can directly attached to the pill
                IconButton(
                    onClick = {
                        if (enableHaptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onDelete(path)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(16.dp) // Tiny trash icon
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 3. The "Show More" Button
        if (hasManyNotes) {
            Text(
                text = if (isExpanded) "Show Less" else "+ ${voiceNotes.size - 3} More",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 4. The Record / Add Button
        if (isRecording) {
            Button(
                onClick = { onStopRecording() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                    contentColor = Color(0xFFE53935)
                ),
                border = BorderStroke(1.dp, Color(0xFFE53935)),
                shape = RoundedCornerShape(50)
                // REMOVED: modifier = Modifier.fillMaxWidth()
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recording...")
            }
        } else {
            OutlinedButton(
                onClick = { onStartRecording() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(50)
                // REMOVED: modifier = Modifier.fillMaxWidth()
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Mic, contentDescription = "Mic", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("+ Add Voice Note")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: List<ProjectFolder>,
    currentFolderId: String?,
    onAssign: (String?) -> Unit,
    onDismiss: () -> Unit
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
                Text("Move to Folder", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (currentFolderId != null) {
                    OutlinedButton(
                        onClick = {
                            onAssign(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFFE53935)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                    ) {
                        Text("Remove from Folder")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssign(folder.id)
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(folder.color))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(folder.name, color = TextPrimary, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTagsSheet(
    allTags: List<TimenoteFolder>,
    selected: List<TimenoteFolder>,
    onToggle: (TimenoteFolder) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 48.dp)) {
                Text("Edit Tags", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(allTags) { tag ->
                        val isSelected = selected.any { it.id == tag.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggle(tag) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tag.color))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(tag.name, color = TextPrimary, fontSize = 16.sp)
                            }
                            if (isSelected) {
                                Icon(androidx.compose.material.icons.Icons.Default.CheckCircle, contentDescription = "Selected", tint = tag.color)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor)
                ) {
                    Text("Save Tags", color = Color.White)
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionEditorSheet(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    otherTimenotes: List<Timenote>,
    focusRequester: FocusRequester,
    enableHaptics: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

        ModalBottomSheet(
            onDismissRequest = onCancel,
            sheetState = sheetState,
            containerColor = SurfaceDark,
        ) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).padding(horizontal = 16.dp, vertical = 8.dp)) {

                // --- TOP BAR ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Text("Edit Note", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        if (enableHaptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onSave()
                    }) {
                        Text("Save", color = DefaultAccentColor, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- PREMIUM MARKDOWN TOOLBAR ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center, // Centers the buttons!
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val btnModifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(BackgroundDark).border(1.dp, TextSecondary.copy(alpha=0.15f), RoundedCornerShape(12.dp))

                    Box(
                        modifier = btnModifier.clickable {
                            val s = text.selection.start
                            val e = text.selection.end
                            val t = text.text
                            val newText = t.substring(0, s) + "**" + t.substring(s, e) + "**" + t.substring(e)
                            onTextChange(text.copy(text = newText, selection = TextRange(e + 4)))
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("B", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) }

                    Spacer(modifier = Modifier.width(16.dp)) // Nice gap between buttons

                    Box(
                        modifier = btnModifier.clickable {
                            val s = text.selection.start
                            val e = text.selection.end
                            val t = text.text
                            val newText = t.substring(0, s) + "_" + t.substring(s, e) + "_" + t.substring(e)
                            onTextChange(text.copy(text = newText, selection = TextRange(e + 2)))
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("I", color = TextPrimary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 18.sp) }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier = btnModifier.clickable {
                            val s = text.selection.start
                            val t = text.text
                            val newText = t.substring(0, s) + "# " + t.substring(s)
                            onTextChange(text.copy(text = newText, selection = TextRange(s + 2)))
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("H1", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Checklist Button
                    Box(
                        modifier = btnModifier.clickable {
                            val s = text.selection.start
                            val t = text.text
                            val newText = t.substring(0, s) + "- [ ] " + t.substring(s)
                            onTextChange(text.copy(text = newText, selection = TextRange(s + 6)))
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("[ ]", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Bullet List Button
                    Box(
                        modifier = btnModifier.clickable {
                            val s = text.selection.start
                            val t = text.text
                            val newText = t.substring(0, s) + "- " + t.substring(s)
                            onTextChange(text.copy(text = newText, selection = TextRange(s + 2)))
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("•", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Subtle divider line under the toolbar
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TextSecondary.copy(alpha=0.1f)))

                // --- AUTO SUGGEST LOGIC ---
                val cursorPosition = text.selection.start
                val textUntilCursor = text.text.substring(0, cursorPosition.coerceAtMost(text.text.length))
                val lastAtIndex = textUntilCursor.lastIndexOf('@')

                var mentionQuery: String? = null
                if (lastAtIndex != -1) {
                    val queryPart = textUntilCursor.substring(lastAtIndex + 1)
                    // If there are no newlines or closing brackets, and it's less than 30 chars, trigger search!
                    if (!queryPart.contains("\n") && !queryPart.contains("]") && queryPart.length < 30) {
                        mentionQuery = queryPart
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = mentionQuery != null) {
                    val suggestions = otherTimenotes.filter { it.title.contains(mentionQuery ?: "", ignoreCase = true) }.take(4) // Show top 4 suggestions

                    if (suggestions.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundDark)
                                .border(1.dp, DefaultAccentColor.copy(alpha=0.5f), RoundedCornerShape(8.dp))
                        ) {
                            items(suggestions) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // INJECT THE SMART LINK!
                                            val t = text.text
                                            val insertText = "@[${suggestion.title}](${suggestion.id}) "
                                            val newText = t.substring(0, lastAtIndex) + insertText + t.substring(cursorPosition)
                                            onTextChange(text.copy(
                                                text = newText,
                                                selection = TextRange(lastAtIndex + insertText.length)
                                            ))
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(suggestion.title, color = TextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // --- THE TEXT EDITOR ---
                // Automatically request focus to pop up the keyboard
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp).focusRequester(focusRequester),
                    placeholder = { Text("Start typing...", color = TextSecondary) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = TextPrimary, lineHeight = 28.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent, // Completely invisible borders
                        unfocusedBorderColor = Color.Transparent,
                    )
                )
            }
        }
}
