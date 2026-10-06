package com.oblutack.timenote.feature_timer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.blur
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.oblutack.timenote.di.timerViewModel
import com.oblutack.timenote.di.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel = timerViewModel()
) {
    val settingsRepository = LocalAppContainer.current.settingsRepository
    val state by viewModel.state.collectAsState()

    val enableHaptics by settingsRepository.enableHapticsFlow.collectAsState(initial = true)
    val haptic = LocalHapticFeedback.current

    val useMonochromeNodes by settingsRepository.useMonochromeNodesFlow.collectAsState(initial = true)
    val customColors by settingsRepository.customColorsFlow.collectAsState(initial = emptyList())
    // 1. Get the Setting
    val enableBlur by settingsRepository.enableBackgroundBlurFlow.collectAsState(initial = true)

    // 2. Check if ANY popup is open
    val isPopupOpen = state.isAddNoteDialogOpen || state.isCategoryPopupOpen || state.isCreateTagDialogOpen || state.isManageTagsSheetOpen

    // 3. The Premium Physics Animation
    val blurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (enableBlur && isPopupOpen) 16.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "BlurAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .blur(radius = blurRadius)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        val parentTitle = state.parentSessionTitle
        if (parentTitle != null) {
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
                    text = parentTitle.uppercase(),
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
        AnimatedVisibility(
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
                                    .background(folder.color, CircleShape)
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
                    onClick = {
                        if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onAction(TimerAction.Start)
                    },
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
                    onClick = {
                        if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // A lighter tap for pause
                        viewModel.onAction(TimerAction.Pause)
                    },
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
                onClick = {
                    if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onAction(TimerAction.End)
                },
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
                        repeatMode = RepeatMode.Reverse
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
                            .background(Color(0xFFE53935), CircleShape)
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

        if (state.voiceMemoUnavailable) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Voice memos need microphone permission. Allow it in the app's system settings.",
                color = Color(0xFFE53935),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
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
            LaunchedEffect(eventCount) {
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
        AddNoteSheet(state = state, customColors = customColors, onAction = viewModel::onAction)
    }

    if (state.isCategoryPopupOpen) {
        SaveTimenoteSheet(state = state, onAction = viewModel::onAction)
    }

    if (state.isCreateTagDialogOpen) {
        CreateTagSheet(state = state, customColors = customColors, onAction = viewModel::onAction)
    }

    if (state.isManageTagsSheetOpen) {
        ManageTagsSheet(state = state, onAction = viewModel::onAction)
    }
}
