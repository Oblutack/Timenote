package com.oblutack.timenote.feature_timer.presentation

import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
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
import com.oblutack.timenote.ui.theme.BackgroundDark
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.FlowRow
import com.oblutack.timenote.feature_history.domain.identityColor
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.blur
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
    val focusManager = LocalFocusManager.current
    val permissions = com.oblutack.timenote.core.LocalPermissionAsker.current

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
                    color = TnTheme.colors.branch,
                    fontSize = TnType.Caption,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = parentTitle.uppercase(),
                    color = TextSecondary,
                    fontSize = TnType.Body,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // The three looks of the timer: ready (idle), running, on a break (paused)
        val isIdle = !state.isRunning && !state.isPaused
        val showLastLabel = isIdle && state.timelineEvents.isNotEmpty()

        // Above the ring: "LAST SESSION" and its title while idle, "ON A BREAK" while paused. The space is always kept,
        // only the opacity changes, so nothing jumps when the state does.
        val topLabelAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (showLastLabel || state.isPaused) 1f else 0f,
            label = "topLabelFade"
        )
        val lastSessionAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (showLastLabel) 1f else 0f,
            label = "lastSessionFade"
        )
        Text(
            text = if (state.isPaused) "ON A BREAK" else "LAST SESSION",
            color = if (state.isPaused) TnTheme.colors.pause else TextSecondary,
            fontSize = TnType.Body,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.alpha(topLabelAlpha)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            // While idle, displayTime holds how long that last session was: shown here, in small type
            text = "${state.lastSessionTitle.ifEmpty { "Untitled Session" }.uppercase()} · ${state.displayTime}",
            color = TextSecondary,
            fontSize = TnType.Body,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.alpha(lastSessionAlpha)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // The ring: the session's colour while running (grey without a tag), amber on a break, only the track when idle.
        // One lap is one hour.
        val ringColor = if (state.isPaused) TnTheme.colors.pause else state.selectedCategories.identityColor(TnTheme.colors.untagged)
        val ringProgress = when {
            state.isPaused -> ringFraction(state.pauseSeconds)
            state.isRunning -> ringFraction(state.elapsedSeconds)
            else -> null
        }
        val pauseAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (state.isPaused) 1f else 0f,
            label = "pauseFadeAnimation"
        )
        // The clock and what belongs under it. Shared by the ring layout and the plain layout used for large text.
        val clockColumn: @Composable (Modifier) -> Unit = { clockModifier ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    // Idle: "Ready". Running: the session clock. On a break: the break, large.
                    text = when {
                        isIdle -> "Ready"
                        state.isPaused -> state.currentPauseTime
                        else -> state.displayTime
                    },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = if (isIdle) null else TnTheme.mono,
                    color = when {
                        isIdle -> TextSecondary
                        state.isPaused -> TnTheme.colors.pause
                        else -> TextPrimary
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    // one line always: with a large system font the clock shrinks instead of wrapping
                    maxLines = 1,
                    softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 48.sp),
                    modifier = clockModifier
                )
                // On a break the session clock stays visible, smaller (it is frozen while paused)
                Text(
                    text = "SESSION ${state.displayTime}",
                    fontFamily = TnTheme.mono,
                    color = TextSecondary,
                    fontSize = TnType.Caption,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    modifier = Modifier.alpha(pauseAlpha)
                )
            }
        }
        // With a large system font the ring would push the buttons off the screen, so the clock stands alone then.
        // On a short screen the ring is smaller.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val windowHeight = with(density) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() }
        if (density.fontScale > RING_MAX_FONT_SCALE) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                clockColumn(Modifier.fillMaxWidth())
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ringSize = minOf(maxWidth, 260.dp, windowHeight * 0.28f)
                TimerRing(progress = ringProgress, color = ringColor, breathing = state.isRunning && !state.isPaused, size = ringSize) {
                    clockColumn(Modifier.width(ringSize * 0.74f))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.sessionTitle,
            onValueChange = { viewModel.onAction(TimerAction.UpdateSessionTitle(it)) },
            placeholder = { Text("Session Title", color = TextSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), // Done closes the keyboard
            modifier = Modifier.fillMaxWidth(),
            shape = TnShape.Medium,
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
                    .clip(TnShape.Pill)
                    .background(if (state.selectedCategories.isNotEmpty()) SurfaceDark else Color.Transparent)
                    .border(1.dp, TextSecondary.copy(alpha = 0.5f), TnShape.Pill)
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
                    fontSize = TnType.Caption,
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
                        .clip(TnShape.Pill)
                        .background(SurfaceDark)
                        .border(1.dp, TextSecondary, TnShape.Pill)
                        .clickable { viewModel.onAction(TimerAction.OpenCreateTagDialog) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "+ New",
                        color = TextSecondary,
                        fontSize = TnType.Caption,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(TnShape.Pill)
                        .background(SurfaceDark)
                        .border(1.dp, TextSecondary, TnShape.Pill)
                        .clickable { viewModel.onAction(TimerAction.OpenManageTagsSheet) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Manage",
                        color = TextSecondary,
                        fontSize = TnType.Caption,
                        fontWeight = FontWeight.Medium
                    )
                }
                val visibleTags = if (state.isTagMenuExpanded) state.availableTags else state.availableTags.take(4)
                visibleTags.forEach { folder ->
                    val isSelected = state.selectedCategories.any { it.id == folder.id }
                    Box(
                        modifier = Modifier
                            .clip(TnShape.Pill)
                            .background(if (isSelected) folder.color.copy(alpha = 0.2f) else SurfaceDark)
                            .then(
                                if (isSelected) Modifier.border(1.dp, folder.color, TnShape.Pill)
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
                                fontSize = TnType.Caption,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (state.availableTags.size > 4) {
                    Box(
                        modifier = Modifier
                            .clip(TnShape.Pill)
                            .background(SurfaceDark)
                            .border(1.dp, TextSecondary, TnShape.Pill)
                            .clickable { viewModel.onAction(TimerAction.ToggleTagMenu) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (!state.isTagMenuExpanded) "+ ${state.availableTags.size - 4}" else "Show Less",
                            color = TextSecondary,
                            fontSize = TnType.Caption,
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
                        // The timer notification needs permission on newer Android; asked here, the first time, then it starts
                        permissions.notifications { viewModel.onAction(TimerAction.Start) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DefaultAccentColor,
                        contentColor = TnTheme.colors.onAccent
                    ),
                    shape = TnShape.Large
                    // no End button next to it while idle, so no gap on its side: Start stays centred
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
                        contentColor = TnTheme.colors.onAccent
                    ),
                    shape = TnShape.Large,
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
                        contentColor = TnTheme.colors.onAccent
                    ),
                    shape = TnShape.Large,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    Spacer(Modifier.width(8.dp))
                    Text("Resume")
                }
            }

            // Only while there is a session to end (while idle it did nothing)
            if (state.isRunning) {
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
                    shape = TnShape.Large
                ) {
                    Text("End")
                }
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
                    // The microphone is asked for here, the first time; a "no" ends in the usual hint below
                    onClick = { permissions.microphone { viewModel.onAction(TimerAction.StartVoiceMemo) } },
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
                        containerColor = TnTheme.colors.danger.copy(alpha = 0.2f),
                        contentColor = TnTheme.colors.danger
                    ),
                    border = BorderStroke(1.dp, TnTheme.colors.danger)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(pulseAlpha)
                            .background(TnTheme.colors.danger, CircleShape)
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
                color = TnTheme.colors.danger,
                fontSize = TnType.Caption,
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
                fontSize = TnType.Caption,
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
