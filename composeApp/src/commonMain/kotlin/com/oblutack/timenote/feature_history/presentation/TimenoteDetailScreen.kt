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
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.KeyboardArrowDown
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimenoteDetailScreen(
    timenoteId: String,
    onBackClick: () -> Unit,
    viewModel: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel { HistoryViewModel() },
    onBranchClick: (parentId: String, waypointId: String) -> Unit = { _, _ -> },
    onTimenoteClick: (String) -> Unit = {}
) {
    val allTimenotes by SessionRepository.timenotes.collectAsState()
    val timenote = allTimenotes.find { it.id == timenoteId }
    val childTimenotes = allTimenotes.filter { it.parentTimenoteId == timenote?.id }
    val playingAudioPath by viewModel.playingAudioPath.collectAsState()
    val recordingTimenoteId by viewModel.recordingTimenoteId.collectAsState()
    val scope = rememberCoroutineScope()

    val folders by SessionRepository.folders.collectAsState()
    var isFolderDialogOpen by remember { mutableStateOf(false) }
    var isNotesOnlyView by remember { mutableStateOf(false) }
    var isVoiceNotesExpanded by remember { mutableStateOf(false) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleText by remember(timenote?.title) {
        val text = timenote?.title ?: ""
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text, TextRange(text.length)))
    }
    val titleFocusRequester = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(isEditingTitle) {
        if (isEditingTitle) titleFocusRequester.requestFocus()
    }
    var isTimelineExpanded by remember { mutableStateOf(false) }

    val cleanDescription = timenote?.description?.let { if (it.contains("waypoints recorded")) "" else it } ?: ""
    var optimisticDescription by remember(timenote?.id) { mutableStateOf<String?>(null) }
    val displayDescription = optimisticDescription ?: cleanDescription
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
    androidx.compose.runtime.LaunchedEffect(isEditingDescription) {
        if (isEditingDescription) {
            focusRequester.requestFocus()
        }
    }
    val scrollState = androidx.compose.foundation.rememberScrollState()
    val useMonochromeNodes by com.oblutack.timenote.data.repository.SettingsRepository.useMonochromeNodesFlow.collectAsState(initial = true)

    if (timenote == null) {
        Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
            Text("Timenote not found", color = TextPrimary)
        }
        return
    }

    val currentFolder = folders.find { it.id == timenote.folderId }

    // --- 1. Date & Time Formatting ---
    // Safely parse the timestamp. If it's 0 (from old mock data), fallback to current time
    val validTimestamp = if (timenote.createdAt > 0L) timenote.createdAt else com.oblutack.timenote.getCurrentTimeMillis()
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

    val allTags by SessionRepository.tags.collectAsState()
    var isEditTagsSheetOpen by remember { mutableStateOf(false) }
    var tempSelectedTags by remember(timenote?.tags) { mutableStateOf(timenote?.tags ?: emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
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
            Text("Timenote Details", color = TextSecondary, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.toggleTimenotePin(timenote.id) }) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (timenote.isPinned) "Unpin" else "Pin",
                    tint = if (timenote.isPinned) DefaultAccentColor else TextSecondary
                )
            }
        }

        androidx.compose.animation.AnimatedContent(
            targetState = isEditingTitle,
            label = "TitleEditAnimation"
        ) { isEditing ->
            if (!isEditing) {
                Text(
                    text = timenote.title,
                    color = TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { isEditingTitle = true }
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            isEditingTitle = false
                            titleText = androidx.compose.ui.text.input.TextFieldValue(timenote.title, TextRange(timenote.title.length))
                        }) { Text("Cancel", color = TextSecondary) }

                        TextButton(onClick = {
                            SessionRepository.updateTimenoteTitle(timenote.id, titleText.text)
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
                .clip(RoundedCornerShape(50))
                .clickable { isFolderDialogOpen = true }
                .background(currentFolder?.color?.copy(alpha = 0.2f) ?: SurfaceDark)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (currentFolder == null) "Unassigned" else currentFolder.name,
                color = currentFolder?.color ?: TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = displayDate,
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = timenote.duration, color = TextSecondary, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Work vs Pause Breakdown Bar
        // --- Define the Colors ---
        val workColor = timenote.tags.firstOrNull()?.color ?: DefaultAccentColor
        val pauseColor = Color(0xFF333333) // Muted Dark Gray for the Pause segment

        // --- 1. The Progress Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
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
            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(workColor))
            Spacer(modifier = Modifier.width(6.dp))
            Text("$workPercent% Work", color = TextSecondary, fontSize = 12.sp)

            Spacer(modifier = Modifier.width(12.dp))

            // Pause Legend
            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(pauseColor)) // <-- Make sure this uses pauseColor!
            Spacer(modifier = Modifier.width(6.dp))
            Text("$pausePercent% Pause", color = TextSecondary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. Description Section ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { isEditingDescription = true }
        ) {
            if (displayDescription.isBlank()) {
                Text("Tap to add a description...", color = TextSecondary)
            } else {
                val dynamicAccentColor = timenote?.tags?.firstOrNull()?.color ?: DefaultAccentColor
                Text(
                    text = com.oblutack.timenote.core.parseMarkdownToAnnotatedString(displayDescription, dynamicAccentColor),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
        }

        // --- VOICE NOTE SECTION ---
        Spacer(modifier = Modifier.height(8.dp))
        // --- VOICE NOTE SECTION ---
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Determine what to show
            val hasManyNotes = timenote.voiceNotes.size > 3
            val displayedNotes = if (isVoiceNotesExpanded) timenote.voiceNotes else timenote.voiceNotes.take(3)

            // 2. Render the visible notes
            displayedNotes.forEachIndexed { index, path ->
                val absoluteIndex = timenote.voiceNotes.indexOf(path).takeIf { it != -1 }?.plus(1) ?: (index + 1)
                val isPlaying = playingAudioPath == path

                Row(
                    modifier = Modifier
                        // REMOVED: .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceDark)
                        .border(1.dp, DefaultAccentColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable { viewModel.playAudio(path) }
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
                        onClick = { viewModel.deleteVoiceNote(timenote.id, path) },
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
                    text = if (isVoiceNotesExpanded) "Show Less" else "+ ${timenote.voiceNotes.size - 3} More",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isVoiceNotesExpanded = !isVoiceNotesExpanded }
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4. The Record / Add Button
            if (recordingTimenoteId == timenote.id) {
                Button(
                    onClick = { viewModel.stopRecordingForTimenote() },
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
                    onClick = { viewModel.startRecordingForTimenote(timenote.id) },
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
        // --- END VOICE NOTE SECTION ---

        Spacer(modifier = Modifier.height(24.dp))


        // --- TAGS SECTION ---
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(timenote.tags) { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, tag.color, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = tag.name, color = tag.color, fontSize = 14.sp)
                }
            }
            // The tiny Edit Button at the end of the row!
            item {
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(SurfaceDark)
                        .clickable {
                            tempSelectedTags = timenote.tags // Reset temp state
                            isEditTagsSheetOpen = true
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Edit Tags", tint = TextSecondary, modifier = Modifier.size(16.dp))
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
                .clip(RoundedCornerShape(8.dp))
                .clickable { isTimelineExpanded = !isTimelineExpanded }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SESSION TIMELINE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${timenote.timelineEvents.size} WAYPOINTS",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
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
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isNotesOnlyView) Color(0xFF2C2C2C) else Color.Transparent)
                            .clickable { isNotesOnlyView = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Timeline", color = if (!isNotesOnlyView) TextPrimary else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isNotesOnlyView) Color(0xFF2C2C2C) else Color.Transparent)
                            .clickable { isNotesOnlyView = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Notes Only", color = if (isNotesOnlyView) TextPrimary else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                            textNotes.forEach { note ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(color = SurfaceDark, shape = RoundedCornerShape(8.dp))
                                        .padding(start = 2.dp) // Space for the solid border
                                        .background(color = SurfaceDark)
                                        .drawBehind {
                                            val strokeWidth = 4.dp.toPx()
                                            drawLine(
                                                color = note.color ?: DefaultAccentColor,
                                                start = androidx.compose.ui.geometry.Offset(x = 0f, y = 0f),
                                                end = androidx.compose.ui.geometry.Offset(x = 0f, y = size.height),
                                                strokeWidth = strokeWidth
                                            )
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(text = note.title, color = TextPrimary, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = note.timestamp, color = TextSecondary, fontSize = 12.sp)
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
        ModalBottomSheet(
            onDismissRequest = { isFolderDialogOpen = false },
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                Text("Move to Folder", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (timenote.folderId != null) {
                    OutlinedButton(
                        onClick = {
                            com.oblutack.timenote.data.repository.SessionRepository.assignFolderToTimenote(timenote.id, null)
                            isFolderDialogOpen = false
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
                                    SessionRepository.assignFolderToTimenote(timenote.id, folder.id)
                                    isFolderDialogOpen = false
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
    if (isEditTagsSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isEditTagsSheetOpen = false },
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 48.dp)) {
                Text("Edit Tags", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(allTags) { tag ->
                        val isSelected = tempSelectedTags.any { it.id == tag.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tempSelectedTags = if (isSelected) {
                                        tempSelectedTags.filter { it.id != tag.id }
                                    } else {
                                        tempSelectedTags + tag
                                    }
                                }
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
                    onClick = {
                        SessionRepository.updateTimenoteTags(timenote.id, tempSelectedTags)
                        isEditTagsSheetOpen = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor)
                ) {
                    Text("Save Tags", color = Color.White)
                }
            }
        }
    }
    if (isEditingDescription) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { isEditingDescription = false },
            sheetState = sheetState,
            containerColor = SurfaceDark,
            modifier = Modifier.fillMaxHeight(0.95f) // Slightly taller
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // --- TOP BAR ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        isEditingDescription = false
                        descriptionText = androidx.compose.ui.text.input.TextFieldValue(
                            text = cleanDescription,
                            selection = TextRange(cleanDescription.length)
                        )
                    }) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Text("Edit Note", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        if (timenote != null) {
                            com.oblutack.timenote.data.repository.SessionRepository.updateTimenoteDescription(timenote.id, descriptionText.text)
                            optimisticDescription = descriptionText.text
                        }
                        isEditingDescription = false
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
                            val s = descriptionText.selection.start
                            val e = descriptionText.selection.end
                            val t = descriptionText.text
                            val newText = t.substring(0, s) + "**" + t.substring(s, e) + "**" + t.substring(e)
                            descriptionText = descriptionText.copy(text = newText, selection = TextRange(e + 4))
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("B", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) }

                    Spacer(modifier = Modifier.width(16.dp)) // Nice gap between buttons

                    Box(
                        modifier = btnModifier.clickable {
                            val s = descriptionText.selection.start
                            val e = descriptionText.selection.end
                            val t = descriptionText.text
                            val newText = t.substring(0, s) + "_" + t.substring(s, e) + "_" + t.substring(e)
                            descriptionText = descriptionText.copy(text = newText, selection = TextRange(e + 2))
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("I", color = TextPrimary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 18.sp) }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier = btnModifier.clickable {
                            val s = descriptionText.selection.start
                            val t = descriptionText.text
                            val newText = t.substring(0, s) + "# " + t.substring(s)
                            descriptionText = descriptionText.copy(text = newText, selection = TextRange(s + 2))
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("H1", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Subtle divider line under the toolbar
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TextSecondary.copy(alpha=0.1f)))

                // --- THE TEXT EDITOR ---
                // Automatically request focus to pop up the keyboard
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
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
}

@Composable
fun TimenoteTimelineItem(
    event: com.oblutack.timenote.feature_timer.domain.TimelineEvent,
    isLastItem: Boolean,
    useMonochrome: Boolean,
    playingAudioPath: String?,
    onPlayAudioClick: (String) -> Unit,
    onBranchClick: () -> Unit,
    childNotes: List<com.oblutack.timenote.feature_history.domain.Timenote> = emptyList(),
    onChildClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 1. Determine size and color based on EventType
                val isStartOrEnd = event.type == EventType.START || event.type == EventType.END
                val circleRadius = if (isStartOrEnd) 7.dp.toPx() else 5.dp.toPx()
                val circleCenterY = 10.dp.toPx()

                val nodeColor = when (event.type) {
                    com.oblutack.timenote.feature_timer.domain.EventType.START -> if (useMonochrome) TextPrimary else Color(0xFF4CAF50)
                    com.oblutack.timenote.feature_timer.domain.EventType.END -> if (useMonochrome) TextSecondary else Color(0xFFE53935)
                    else -> event.color ?: DefaultAccentColor
                }

                drawCircle(
                    color = nodeColor,
                    radius = circleRadius,
                    center = Offset(size.width / 2, circleCenterY),
                    style = Stroke(width = if (isStartOrEnd) 2.dp.toPx() else 1.5.dp.toPx())
                )

                drawCircle(
                    color = nodeColor,
                    radius = circleRadius * 0.5f,
                    center = Offset(size.width / 2, circleCenterY)
                )

                if (!isLastItem) {
                    val lineStartY = circleCenterY + circleRadius + 4.dp.toPx()
                    drawLine(
                        color = SurfaceDark,
                        start = Offset(size.width / 2, lineStartY),
                        end = Offset(size.width / 2, size.height + 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp, end = 8.dp)
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

            if (event.audioPath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isPlaying = playingAudioPath == event.audioPath
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceDark)
                        .border(1.dp, DefaultAccentColor, RoundedCornerShape(50))
                        .clickable { onPlayAudioClick(event.audioPath!!) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play Voice Memo",
                        tint = DefaultAccentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlaying) "Pause" else "Play Voice Memo",
                        color = DefaultAccentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val waypointChildren = childNotes.filter { it.parentWaypointId == event.id }
            if (waypointChildren.isNotEmpty()) {
                waypointChildren.forEach { child ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DefaultAccentColor.copy(alpha = 0.1f))
                            .border(1.dp, DefaultAccentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onChildClick(child.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↳ Branched: ${child.title}",
                            color = DefaultAccentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (event.type == EventType.NOTE) {
            IconButton(onClick = onBranchClick) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = "Branch Timer",
                    tint = TextSecondary
                )
            }
        }
    }
}
