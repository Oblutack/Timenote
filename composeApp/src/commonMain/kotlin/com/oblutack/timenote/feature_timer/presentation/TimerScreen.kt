package com.oblutack.timenote.feature_timer.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.oblutack.timenote.feature_history.domain.mockFolders
import com.oblutack.timenote.feature_timer.domain.EventType
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel = viewModel { TimerViewModel() }
) {
    val state by viewModel.state.collectAsState()

    val useMonochromeNodes by com.oblutack.timenote.data.repository.SettingsRepository.useMonochromeNodesFlow.collectAsState(initial = true)
    val customColors by com.oblutack.timenote.data.repository.SettingsRepository.customColorsFlow.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        if (state.parentSessionTitle != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BRANCHED FROM:",
                    color = Color(0xFF9C27B0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.parentSessionTitle!!.uppercase(),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // --- NEW: Smooth Fade-In "Last Session" Label ---
        // 1. Determine if we should show it
        val showLastLabel = !state.isRunning && !state.isPaused && state.timelineEvents.isNotEmpty()

        // 2. Animate the opacity (alpha) from 0f (invisible) to 1f (fully visible)
        val labelAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (showLastLabel) 1f else 0f,
            label = "fadeAnimation"
        )

        // 3. Always draw the text to reserve the space, but apply the animated alpha!
        Text(
            text = "YOUR LAST TIMENOTE",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.alpha(labelAlpha)
        )

        // --- NEW: Display the actual name of the last session ---
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.lastSessionTitle.ifEmpty { "Untitled Session" }.uppercase(), // <-- Uppercase added here!
            color = TextSecondary,             // <-- Changed to Gray
            fontSize = 14.sp,                  // <-- Matched size
            fontWeight = FontWeight.SemiBold,  // <-- Matched weight
            letterSpacing = 1.5.sp,            // <-- Matched premium letter spacing
            modifier = Modifier.alpha(labelAlpha)
        )
        // --------------------------------------------------------

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.displayTime,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            color = TextPrimary
        )

        // --- NEW: Smooth Fade-In Pause Timer ---
        val pauseAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (state.isPaused) 1f else 0f,
            label = "pauseFadeAnimation"
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PAUSED: ${state.currentPauseTime}",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.alpha(pauseAlpha)
        )
        // ---------------------------------------

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.sessionTitle,
            onValueChange = { viewModel.onAction(TimerAction.UpdateSessionTitle(it)) },
            placeholder = { Text("Session Title", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (state.selectedCategories.isNotEmpty()) SurfaceDark else Color.Transparent)
                    .border(1.dp, TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable { viewModel.onAction(TimerAction.ToggleTagsRowVisibility) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = when {
                        state.isTagsRowVisible -> "- Tags"
                        state.selectedCategories.isNotEmpty() -> "${state.selectedCategories.size} Tags Selected"
                        else -> "+ Tags"
                    },
                    color = if (state.selectedCategories.isNotEmpty() && !state.isTagsRowVisible) TextPrimary else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- WRAPPED IN ANIMATED VISIBILITY ---
        androidx.compose.animation.AnimatedVisibility(
            visible = state.isTagsRowVisible
        ) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceDark)
                        .border(1.dp, TextSecondary, RoundedCornerShape(50))
                        .clickable { viewModel.onAction(TimerAction.OpenCreateTagDialog) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "+ New",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceDark)
                        .border(1.dp, TextSecondary, RoundedCornerShape(50))
                        .clickable { viewModel.onAction(TimerAction.OpenManageTagsSheet) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Manage",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                val visibleTags = if (state.isTagMenuExpanded) state.availableTags else state.availableTags.take(4)
                visibleTags.forEach { folder ->
                    val isSelected = state.selectedCategories.any { it.id == folder.id }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) folder.color.copy(alpha = 0.2f) else SurfaceDark)
                            .then(
                                if (isSelected) Modifier.border(1.dp, folder.color, RoundedCornerShape(50))
                                else Modifier
                            )
                            .clickable { viewModel.onAction(TimerAction.ToggleCategory(folder)) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(folder.color, androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = folder.name,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (state.availableTags.size > 4) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceDark)
                            .border(1.dp, TextSecondary, RoundedCornerShape(50))
                            .clickable { viewModel.onAction(TimerAction.ToggleTagMenu) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (!state.isTagMenuExpanded) "+ ${state.availableTags.size - 4}" else "Show Less",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!state.isRunning) {
                Button(
                    onClick = { viewModel.onAction(TimerAction.Start) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DefaultAccentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                    Spacer(Modifier.width(8.dp))
                    Text("Start")
                }
            } else if (!state.isPaused) {
                Button(
                    onClick = { viewModel.onAction(TimerAction.Pause) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DefaultAccentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                    Spacer(Modifier.width(8.dp))
                    Text("Pause")
                }
            } else {
                Button(
                    onClick = { viewModel.onAction(TimerAction.Resume) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DefaultAccentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    Spacer(Modifier.width(8.dp))
                    Text("Resume")
                }
            }

            OutlinedButton(
                onClick = { viewModel.onAction(TimerAction.End) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary,
                    containerColor = SurfaceDark
                ),
                border = null,
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("End")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { viewModel.onAction(TimerAction.OpenAddNoteDialog) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary
                ),
                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
            ) {
                Text("+ Add Note")
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (!state.isRecordingVoiceMemo) {
                OutlinedButton(
                    onClick = { viewModel.onAction(TimerAction.StartVoiceMemo) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary
                    ),
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Memo",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+ Voice Memo")
                }
            } else {
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(800),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "PulseAlpha"
                )

                Button(
                    onClick = { viewModel.onAction(TimerAction.StopVoiceMemo) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                        contentColor = Color(0xFFE53935)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE53935))
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(pulseAlpha)
                            .background(Color(0xFFE53935), androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recording...", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))


        Column(
            // --- FIX 1: Add weight(1f) so it perfectly fills the remaining screen and scrolls smoothly! ---
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "TIMELINE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // --- FIX 2: Auto-scroll state ---
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val eventCount = state.timelineEvents.size

            // Whenever the number of events changes, smoothly snap back to the top!
            androidx.compose.runtime.LaunchedEffect(eventCount) {
                if (eventCount > 0) {
                    listState.animateScrollToItem(0)
                }
            }

            LazyColumn(
                state = listState, // <--- Attach the scroll state
                modifier = Modifier.fillMaxSize() // <--- Let it fill the weight bounds
            ) {
                itemsIndexed(
                    items = state.timelineEvents,
                    key = { _, event -> "${event.id}_${event.hashCode()}" } // CRUCIAL: Tells Compose this is a unique item!
                ) { index, event ->
                    TimelineItem(
                        event = event,
                        isLastItem = index == state.timelineEvents.size - 1,
                        useMonochrome = useMonochromeNodes
                    )
                }
            }
        }
    }

    if (state.isAddNoteDialogOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onAction(TimerAction.CloseAddNoteDialog) },
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
                    onValueChange = { viewModel.onAction(TimerAction.UpdateDialogNoteText(it)) },
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
                                .background(color, androidx.compose.foundation.shape.CircleShape)
                                .let {
                                    if (color == state.dialogNoteColor) {
                                        it.border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                    } else it
                                }
                                .clickable { viewModel.onAction(TimerAction.UpdateDialogNoteColor(color)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onAction(TimerAction.CloseAddNoteDialog) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { viewModel.onAction(TimerAction.SaveNote) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                    ) {
                        Text("Save Note")
                    }
                }
            }
        }
    }

    if (state.isCategoryPopupOpen) {

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { viewModel.onAction(TimerAction.SkipCategoriesAndSave) },
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
                                    .clickable { viewModel.onAction(TimerAction.SelectFolder(folder)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(folder.color, androidx.compose.foundation.shape.CircleShape))
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
                                .clickable { viewModel.onAction(TimerAction.ToggleCategory(folder)) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(folder.color, androidx.compose.foundation.shape.CircleShape)
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
                                .clickable { viewModel.onAction(TimerAction.OpenCreateTagDialog) }
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
                                .clickable { viewModel.onAction(TimerAction.OpenManageTagsSheet) }
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
                        onClick = { viewModel.onAction(TimerAction.SkipCategoriesAndSave) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip", color = TextSecondary)
                    }
                    Button(
                        onClick = { viewModel.onAction(TimerAction.ConfirmCategoriesAndSave) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (state.isCreateTagDialogOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onAction(TimerAction.CloseCreateTagDialog) },
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
                    onValueChange = { viewModel.onAction(TimerAction.UpdateNewTagName(it)) },
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
                    onValueChange = { viewModel.onAction(TimerAction.UpdateNewTagDescription(it)) },
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
                                .background(color, androidx.compose.foundation.shape.CircleShape)
                                .let {
                                    if (color == state.newTagColor) {
                                        it.border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                    } else it
                                }
                                .clickable { viewModel.onAction(TimerAction.UpdateNewTagColor(color)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onAction(TimerAction.CloseCreateTagDialog) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { viewModel.onAction(TimerAction.SaveNewTag) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (state.isManageTagsSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onAction(TimerAction.CloseManageTagsSheet) },
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
                                            .background(tag.color, androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = tag.name,
                                            color = TextPrimary,
                                            fontSize = 16.sp
                                        )
                                        if (!tag.description.isNullOrBlank()) {
                                            Text(
                                                text = tag.description!!,
                                                color = TextSecondary.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Row {
                                    IconButton(onClick = { viewModel.onAction(TimerAction.EditTag(tag)) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
                                    }
                                    IconButton(onClick = { viewModel.onAction(TimerAction.DeleteTag(tag.id)) }) {
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
}

@Composable
fun TimelineItem(event: com.oblutack.timenote.feature_timer.domain.TimelineEvent, isLastItem: Boolean, useMonochrome: Boolean) { // <--- ADD useMonochrome: Boolean

    // 1. Sleek Fade-In Animation (Doesn't break list height!)
    val alpha = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(event.id) {
        alpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(500))
    }

    val isStartOrEnd = event.type == com.oblutack.timenote.feature_timer.domain.EventType.START || event.type == com.oblutack.timenote.feature_timer.domain.EventType.END
    val circleRadius = if (isStartOrEnd) 7.dp else 5.dp
    val nodeColor = when (event.type) {
        com.oblutack.timenote.feature_timer.domain.EventType.START -> if (useMonochrome) TextPrimary else Color(0xFF4CAF50)
        com.oblutack.timenote.feature_timer.domain.EventType.END -> if (useMonochrome) TextSecondary else Color(0xFFE53935)
        else -> event.color ?: DefaultAccentColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha.value) // Applies the fade-in
            .padding(bottom = 8.dp)
            // 2. Draw the vertical line in the background (Removes the need for IntrinsicSize.Min!)
            .drawBehind {
                if (!isLastItem) {
                    val circleCenterY = 10.dp.toPx()
                    val lineStartY = circleCenterY + circleRadius.toPx() + 4.dp.toPx()
                    // Draw line straight down based on the actual height of the text column
                    drawLine(
                        color = SurfaceDark,
                        start = androidx.compose.ui.geometry.Offset(12.dp.toPx(), lineStartY),
                        end = androidx.compose.ui.geometry.Offset(12.dp.toPx(), size.height + 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            },
        verticalAlignment = Alignment.Top
    ) {
        // 3. Just draw the circle here, no fillMaxHeight needed
        Box(
            modifier = Modifier.width(24.dp).padding(top = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
                drawCircle(
                    color = nodeColor,
                    radius = circleRadius.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, 0f),
                    style = Stroke(width = if (isStartOrEnd) 2.dp.toPx() else 1.5.dp.toPx())
                )
                drawCircle(
                    color = nodeColor,
                    radius = circleRadius.toPx() * 0.5f,
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, 0f)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 4. The Text Column dictates the natural height of the Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = event.title,
                color = TextPrimary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.timestamp,
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}
