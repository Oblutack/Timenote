package com.oblutack.timenote.feature_history.presentation

import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.ui.theme.BackgroundDark
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.feature_timer.domain.EventType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import com.oblutack.timenote.getCurrentTimeMillis
import com.oblutack.timenote.di.historyViewModel
import com.oblutack.timenote.di.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimenoteDetailScreen(
    timenoteId: String,
    onBackClick: () -> Unit,
    viewModel: HistoryViewModel = historyViewModel(),
    onBranchClick: (parentId: String, waypointId: String) -> Unit = { _, _ -> },
    onTimenoteClick: (String) -> Unit = {}
) {
    val sessionRepository = LocalAppContainer.current.sessionRepository
    val settingsRepository = LocalAppContainer.current.settingsRepository
    val allTimenotes by sessionRepository.timenotes.collectAsState()
    val timenote = allTimenotes.find { it.id == timenoteId }
    val childTimenotes = allTimenotes.filter { it.parentTimenoteId == timenote?.id }
    val playingAudioPath by viewModel.playingAudioPath.collectAsState()
    val recordingTimenoteId by viewModel.recordingTimenoteId.collectAsState()
    val micUnavailable by viewModel.micUnavailable.collectAsState()
    val downloadingAudio by viewModel.downloadingAudio.collectAsState()
    val audioMessage by viewModel.audioMessage.collectAsState()
    val scope = rememberCoroutineScope()
    val permissions = com.oblutack.timenote.core.LocalPermissionAsker.current

    val folders by sessionRepository.folders.collectAsState()
    var isFolderDialogOpen by remember { mutableStateOf(false) }
    var isNotesOnlyView by remember { mutableStateOf(false) }
    var isVoiceNotesExpanded by remember { mutableStateOf(false) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleText by remember(timenote?.title) {
        val text = timenote?.title ?: ""
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val titleFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isEditingTitle) {
        if (isEditingTitle) titleFocusRequester.requestFocus()
    }
    var isTimelineExpanded by remember { mutableStateOf(false) }

    val cleanDescription = timenote?.description?.let { if (it.contains("waypoints recorded")) "" else it } ?: ""
    var optimisticDescription by remember(timenote?.id) { mutableStateOf<String?>(null) }
    val displayDescription = optimisticDescription ?: cleanDescription
    // Once the database value catches up with the optimistic one, go back to showing the database
    // value, so later changes (e.g. checkbox toggles) are not hidden behind a stale local copy.
    androidx.compose.runtime.LaunchedEffect(cleanDescription) {
        if (optimisticDescription == cleanDescription) optimisticDescription = null
    }
    var isEditingDescription by remember { mutableStateOf(false) }
    var descriptionText by remember(cleanDescription) {
        mutableStateOf(
            TextFieldValue(
                text = cleanDescription,
                selection = TextRange(cleanDescription.length) // <--- Places cursor at the very end!
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isEditingDescription) {
        if (isEditingDescription) {
            focusRequester.requestFocus()
        }
    }
    val scrollState = androidx.compose.foundation.rememberScrollState()
    val useMonochromeNodes by settingsRepository.useMonochromeNodesFlow.collectAsState(initial = true)

    if (timenote == null) {
        Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
            Text("Timenote not found", color = TextPrimary)
        }
        return
    }

    val currentFolder = folders.find { it.id == timenote.folderId }

    // --- 1. Date & Time Formatting ---
    // Safely parse the timestamp. If it's 0 (from old mock data), fallback to current time
    val validTimestamp = if (timenote.createdAt > 0L) timenote.createdAt else getCurrentTimeMillis()
    val instant = Instant.fromEpochMilliseconds(validTimestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    // Format manually to keep it perfectly KMP-safe across iOS/Android
    val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val hour12 = if (dateTime.hour % 12 == 0) 12 else dateTime.hour % 12
    val amPm = if (dateTime.hour >= 12) "PM" else "AM"
    val minuteStr = dateTime.minute.toString().padStart(2, '0')
    val displayDate = "$month ${dateTime.dayOfMonth}, ${dateTime.year} • $hour12:$minuteStr $amPm"

    // --- 2. Work vs Pause Breakdown Math ---
    val totalSeconds = timenote.activeSeconds + timenote.pauseSeconds

    // Protect against division by zero
    val workRatio = if (totalSeconds > 0) timenote.activeSeconds.toFloat() / totalSeconds.toFloat() else 1f
    val pauseRatio = if (totalSeconds > 0) timenote.pauseSeconds.toFloat() / totalSeconds.toFloat() else 0f

    // THE FIX: Round the first value mathematically, then subtract from 100 to guarantee a perfect 100% total!
    val workPercent = if (totalSeconds > 0) kotlin.math.round(workRatio * 100).toInt() else 100
    val pausePercent = if (totalSeconds > 0) 100 - workPercent else 0

    val allTags by sessionRepository.tags.collectAsState()
    var isEditTagsSheetOpen by remember { mutableStateOf(false) }
    var tempSelectedTags by remember(timenote?.tags) { mutableStateOf(timenote?.tags ?: emptyList()) }

    val enableBlur by settingsRepository.enableBackgroundBlurFlow.collectAsState(initial = true)

    // Check if ANY popup is open on the Details screen
    val isPopupOpen = isFolderDialogOpen || isEditingDescription || isEditTagsSheetOpen

    val blurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (enableBlur && isPopupOpen) 16.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "DetailsBlur"
    )

    val enableHaptics by settingsRepository.enableHapticsFlow.collectAsState(initial = true)
    val haptic = LocalHapticFeedback.current


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .blur(radius = blurRadius)
            .verticalScroll(scrollState)
            .padding(top = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Timenote Details", color = TextSecondary, fontSize = TnType.Subtitle, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.toggleTimenotePin(timenote.id) }) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (timenote.isPinned) "Unpin" else "Pin",
                    tint = if (timenote.isPinned) DefaultAccentColor else TextSecondary
                )
            }
        }

        AnimatedContent(
            targetState = isEditingTitle,
            label = "TitleEditAnimation"
        ) { isEditing ->
            if (!isEditing) {
                Text(
                    text = timenote.title,
                    color = TextPrimary,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { isEditingTitle = true }
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester),
                        textStyle = TextStyle(fontSize = TnType.Headline, fontWeight = FontWeight.Bold, color = TextPrimary),
                        shape = TnShape.Medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            isEditingTitle = false
                            titleText = TextFieldValue(timenote.title, TextRange(timenote.title.length))
                        }) { Text("Cancel", color = TextSecondary) }

                        TextButton(onClick = {
                            sessionRepository.updateTimenoteTitle(timenote.id, titleText.text)
                            isEditingTitle = false
                        }) { Text("Save", color = DefaultAccentColor) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Folder Pill
        Box(
            modifier = Modifier
                .clip(TnShape.Pill)
                .clickable { isFolderDialogOpen = true }
                .background(currentFolder?.color?.copy(alpha = 0.2f) ?: SurfaceDark)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (currentFolder == null) "Unassigned" else currentFolder.name,
                color = currentFolder?.color ?: TextSecondary,
                fontSize = TnType.Body,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = displayDate,
            color = TextSecondary,
            fontSize = TnType.Body,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = timenote.duration, color = TextSecondary, fontSize = TnType.BodyLarge)

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Work vs Pause Breakdown Bar
        // --- Define the Colors ---
        val workColor = timenote.tags.firstOrNull()?.color ?: DefaultAccentColor
        val pauseColor = TnTheme.colors.surfaceHigh // Muted Dark Gray for the Pause segment

        // --- 1. The Progress Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(TnShape.Pill)
        ) {
            // Work Segment
            Box(
                modifier = Modifier
                    .weight(workRatio.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(workColor)
            )
            // Pause Segment
            Box(
                modifier = Modifier
                    .weight(pauseRatio.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(pauseColor) // <-- Make sure this uses pauseColor!
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 2. The Legend (Text & Dots) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Work Legend
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(workColor))
            Spacer(modifier = Modifier.width(6.dp))
            Text("$workPercent% Work", color = TextSecondary, fontSize = TnType.Caption)

            Spacer(modifier = Modifier.width(12.dp))

            // Pause Legend
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(pauseColor)) // <-- Make sure this uses pauseColor!
            Spacer(modifier = Modifier.width(6.dp))
            Text("$pausePercent% Pause", color = TextSecondary, fontSize = TnType.Caption)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Other versions of the text (only when two devices edited it at the same time) ---
        val textConflicts by viewModel.textConflicts.collectAsState()
        TextConflictBanner(
            conflicts = textConflicts.filter { it.noteId == timenoteId },
            onRestore = { viewModel.restoreTextConflict(timenoteId, it) },
            onDismiss = { viewModel.dismissTextConflict(timenoteId, it) }
        )

        // --- 2. Description Section ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 80.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isEditingDescription = true }
                .padding(vertical = 16.dp)
        ) {
            if (displayDescription.isBlank()) {
                Text("Tap to add a description...", color = TextSecondary)
            } else {
                val dynamicAccentColor = timenote?.tags?.firstOrNull()?.color ?: DefaultAccentColor
                val lines = displayDescription.split("\n")

                // THE FIX: Render line-by-line as Compose Blocks!
                Column(modifier = Modifier.fillMaxWidth()) {
                    lines.forEachIndexed { lineIndex, line ->
                        val trimmed = line.trimStart()
                        val isUnchecked = trimmed.startsWith("- [ ]")
                        val isChecked = trimmed.startsWith("- [x]", ignoreCase = true)
                        val isBullet = trimmed.startsWith("- ") && !isUnchecked && !isChecked

                        when {
                            // 1. RENDER NATIVE CHECKBOXES
                            isUnchecked || isChecked -> {
                                val content = line.substringAfter("] ")
                                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Icon(
                                        imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Checkbox",
                                        tint = if (isChecked) dynamicAccentColor else TextSecondary,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val newLines = lines.toMutableList()
                                                newLines[lineIndex] = if (isChecked) line.replaceFirst(Regex("\\[[xX]\\]"), "[ ]") else line.replaceFirst("[ ]", "[x]")
                                                val toggled = newLines.joinToString("\n")
                                                optimisticDescription = toggled
                                                sessionRepository.updateTimenoteDescription(timenote.id, toggled)
                                            }
                                    )
                                    Spacer(Modifier.width(8.dp))

                                    // Auto-strikethrough if checked!
                                    val textToParse = if (isChecked) "~~$content~~" else content
                                    val annotatedText = com.oblutack.timenote.core.parseMarkdownToAnnotatedString(textToParse, dynamicAccentColor)

                                    ClickableText(
                                        text = annotatedText,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = TextStyle(color = if (isChecked) TextSecondary else TextPrimary, fontSize = TnType.BodyLarge, lineHeight = 24.sp),
                                        onClick = { offset ->
                                            val annotations = annotatedText.getStringAnnotations(tag = "MENTION", start = offset, end = offset)
                                            if (annotations.isNotEmpty()) onTimenoteClick(annotations.first().item)
                                            else isEditingDescription = true
                                        }
                                    )
                                }
                            }

                            // 2. RENDER NATIVE BULLET POINTS
                            isBullet -> {
                                val content = line.substringAfter("- ")
                                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("•", color = TextPrimary, fontSize = TnType.Subtitle, modifier = Modifier.padding(end = 8.dp))
                                    val annotatedText = com.oblutack.timenote.core.parseMarkdownToAnnotatedString(content, dynamicAccentColor)
                                    ClickableText(
                                        text = annotatedText,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = TextStyle(color = TextPrimary, fontSize = TnType.BodyLarge, lineHeight = 24.sp),
                                        onClick = { offset ->
                                            val annotations = annotatedText.getStringAnnotations(tag = "MENTION", start = offset, end = offset)
                                            if (annotations.isNotEmpty()) onTimenoteClick(annotations.first().item)
                                            else isEditingDescription = true
                                        }
                                    )
                                }
                            }

                            // 3. RENDER NORMAL TEXT
                            else -> {
                                val annotatedText = com.oblutack.timenote.core.parseMarkdownToAnnotatedString(line, dynamicAccentColor)
                                ClickableText(
                                    text = annotatedText,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = TextStyle(color = TextPrimary, fontSize = TnType.BodyLarge, lineHeight = 24.sp),
                                    onClick = { offset ->
                                        val annotations = annotatedText.getStringAnnotations(tag = "MENTION", start = offset, end = offset)
                                        if (annotations.isNotEmpty()) onTimenoteClick(annotations.first().item)
                                        else isEditingDescription = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        VoiceNotesSection(
            voiceNotes = timenote.voiceNotes,
            isExpanded = isVoiceNotesExpanded,
            onToggleExpanded = { isVoiceNotesExpanded = !isVoiceNotesExpanded },
            playingAudioPath = playingAudioPath,
            isRecording = recordingTimenoteId == timenote.id,
            enableHaptics = enableHaptics,
            onPlay = { viewModel.playAudio(it) },
            onDelete = { viewModel.deleteVoiceNote(timenote.id, it) },
            // The microphone is asked for here, the first time; a "no" ends in the usual hint below the button
            onStartRecording = { permissions.microphone { viewModel.startRecordingForTimenote(timenote.id) } },
            onStopRecording = { viewModel.stopRecordingForTimenote() },
            micUnavailable = micUnavailable,
            downloadingAudio = downloadingAudio,
            audioMessage = audioMessage,
            onDismissAudioMessage = { viewModel.dismissAudioMessage() }
        )

        Spacer(modifier = Modifier.height(24.dp))


        // --- TAGS SECTION ---
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(timenote.tags) { tag ->
                Box(
                    modifier = Modifier
                        .clip(TnShape.Pill)
                        .border(1.dp, tag.color, TnShape.Pill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = tag.name, color = tag.color, fontSize = TnType.Body)
                }
            }
            // The tiny Edit Button at the end of the row!
            item {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .clickable {
                            tempSelectedTags = timenote.tags // Reset temp state
                            isEditTagsSheetOpen = true
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Tags", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))


        // 3. Collapsible Timeline (Accordion)
        val rotation by animateFloatAsState(targetValue = if (isTimelineExpanded) 180f else 0f)
        val textNotes = timenote.timelineEvents.filter { it.type == EventType.NOTE }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TnShape.Small)
                .clickable { isTimelineExpanded = !isTimelineExpanded }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SESSION TIMELINE",
                color = TextSecondary,
                fontSize = TnType.Caption,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${timenote.timelineEvents.size} WAYPOINTS",
                    color = TextSecondary,
                    fontSize = TnType.Caption,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Timeline",
                    tint = TextSecondary,
                    modifier = Modifier.rotate(if (isTimelineExpanded) 180f else 0f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. THE EXPANDED CONTENT (Tabs + List)
        AnimatedVisibility(visible = isTimelineExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // --- MINI TABS (Timeline vs Notes) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .clip(TnShape.Pill) // Fully rounded pill
                        .border(1.dp, TextSecondary.copy(alpha = 0.2f), TnShape.Pill) // Ghost border
                        .padding(4.dp), // Padding inside the border so the active tab floats
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val activeBg = SurfaceDark // Or TnTheme.colors.surfaceRaised if you want it slightly lighter

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(TnShape.Pill)
                            .background(if (!isNotesOnlyView) activeBg else Color.Transparent)
                            .clickable {
                                if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isNotesOnlyView = false
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Timeline",
                            color = if (!isNotesOnlyView) TextPrimary else TextSecondary,
                            fontSize = TnType.Label,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(TnShape.Pill)
                            .background(if (isNotesOnlyView) activeBg else Color.Transparent)
                            .clickable {
                                if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isNotesOnlyView = true
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Notes Only",
                            color = if (isNotesOnlyView) TextPrimary else TextSecondary,
                            fontSize = TnType.Label,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                // -------------------------------------

                // --- THE ACTUAL LISTS ---
                if (!isNotesOnlyView) {
                    // TIMELINE VIEW
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        timenote.timelineEvents.forEachIndexed { index, event ->
                            TimenoteTimelineItem(
                                event = event,
                                isLastItem = index == timenote.timelineEvents.size - 1,
                                useMonochrome = useMonochromeNodes,
                                playingAudioPath = playingAudioPath,
                                onPlayAudioClick = { viewModel.playAudio(it) },
                                onBranchClick = { onBranchClick(timenote.id, event.id) },
                                childNotes = childTimenotes,
                                onChildClick = onTimenoteClick
                            )
                        }
                    }
                } else {
                    // NOTES ONLY VIEW
                    if (textNotes.isEmpty()) {
                        Text(
                            text = "No written notes in this session.",
                            color = TextSecondary,
                            modifier = Modifier.padding(24.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val stripeFallback = DefaultAccentColor
                            textNotes.forEach { note ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(color = SurfaceDark, shape = TnShape.Small)
                                        .padding(start = 2.dp) // Space for the solid border
                                        .background(color = SurfaceDark)
                                        .drawBehind {
                                            val strokeWidth = 4.dp.toPx()
                                            drawLine(
                                                color = note.color ?: stripeFallback,
                                                start = Offset(x = 0f, y = 0f),
                                                end = Offset(x = 0f, y = size.height),
                                                strokeWidth = strokeWidth
                                            )
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(text = note.title, color = TextPrimary, fontSize = TnType.BodyLarge)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = note.timestamp, color = TextSecondary, fontSize = TnType.Caption)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (isFolderDialogOpen) {
        FolderPickerSheet(
            folders = folders,
            currentFolderId = timenote.folderId,
            onAssign = {
                sessionRepository.assignFolderToTimenote(timenote.id, it)
                isFolderDialogOpen = false
            },
            onDismiss = { isFolderDialogOpen = false }
        )
    }
    if (isEditTagsSheetOpen) {
        EditTagsSheet(
            allTags = allTags,
            selected = tempSelectedTags,
            onToggle = { tag ->
                tempSelectedTags = if (tempSelectedTags.any { it.id == tag.id }) {
                    tempSelectedTags.filter { it.id != tag.id }
                } else {
                    tempSelectedTags + tag
                }
            },
            onSave = {
                sessionRepository.updateTimenoteTags(timenote.id, tempSelectedTags)
                isEditTagsSheetOpen = false
            },
            onDismiss = { isEditTagsSheetOpen = false }
        )
    }
    if (isEditingDescription) {
        DescriptionEditorSheet(
            text = descriptionText,
            onTextChange = { descriptionText = it },
            otherTimenotes = allTimenotes.filter { it.id != timenote.id },
            focusRequester = focusRequester,
            enableHaptics = enableHaptics,
            hasChanges = descriptionText.text != cleanDescription,
            onDismiss = {
                // Closed without choosing: keep what was typed
                if (descriptionText.text != cleanDescription) {
                    sessionRepository.updateTimenoteDescription(timenote.id, descriptionText.text)
                    optimisticDescription = descriptionText.text
                }
                isEditingDescription = false
            },
            onCancel = {
                isEditingDescription = false
                descriptionText = TextFieldValue(
                    text = cleanDescription,
                    selection = TextRange(cleanDescription.length)
                )
            },
            onSave = {
                sessionRepository.updateTimenoteDescription(timenote.id, descriptionText.text)
                optimisticDescription = descriptionText.text
                isEditingDescription = false
            }
        )
    }
}
