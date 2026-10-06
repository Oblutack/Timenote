package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.DefaultAccentColor
import androidx.compose.material3.IconButton
import kotlinx.datetime.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import com.oblutack.timenote.getCurrentTimeMillis
import com.oblutack.timenote.di.historyViewModel
import com.oblutack.timenote.di.LocalAppContainer

fun getDaysInMonth(month: Int, year: Int): Int {
    return when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onTimenoteClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    onTrashClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onGraphClick: () -> Unit,
    viewModel: HistoryViewModel = historyViewModel()
) {
    val settingsRepository = LocalAppContainer.current.settingsRepository
    val recentSessions by viewModel.sessions.collectAsState()
    val folders by viewModel.folders.collectAsState(initial = emptyList())

    var isCalendarView by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val today = remember {
        Instant.fromEpochMilliseconds(getCurrentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var currentMonth by remember { mutableStateOf(today) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    // The query lives in the ViewModel and survives navigation, so reopen the search bar if one is active
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotBlank()) }

    val sessionsByDate = remember(recentSessions) {
        recentSessions.groupBy {
            Instant.fromEpochMilliseconds(if (it.createdAt > 0L) it.createdAt else getCurrentTimeMillis())
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
        }
    }
    
    val displaySessions = if (isCalendarView && selectedDate != null) {
        sessionsByDate[selectedDate] ?: emptyList()
    } else {
        recentSessions
    }

    val selectedFilterTags by viewModel.selectedFilterTags.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val allTags by viewModel.tags.collectAsState(initial = emptyList())

    var isSortSheetOpen by remember { mutableStateOf(false) }
    var isTagFilterSheetOpen by remember { mutableStateOf(false) }

    // --- Apply Filters and Sort ---
    val tagFiltered = remember(displaySessions, selectedFilterTags) {
        if (selectedFilterTags.isEmpty()) displaySessions else displaySessions.filter { session ->
            session.tags.any { tag -> selectedFilterTags.contains(tag.id) }
        }
    }

    val searchFiltered = remember(tagFiltered, searchQuery) {
        if (searchQuery.isBlank()) tagFiltered else tagFiltered.filter { session ->
            session.title.contains(searchQuery, ignoreCase = true) ||
            session.description.contains(searchQuery, ignoreCase = true) ||
            session.tags.any { it.name.contains(searchQuery, ignoreCase = true) } ||
            session.timelineEvents.any { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    val finalDisplaySessions = remember(searchFiltered, sortOption) {
        when (sortOption) {
            // THE FIX: Explicitly cast to Long to ensure perfect mathematical sorting
            SortOption.NEWEST -> searchFiltered.sortedByDescending { it.createdAt.toLong() }
            SortOption.OLDEST -> searchFiltered.sortedBy { it.createdAt.toLong() }
            SortOption.LONGEST -> searchFiltered.sortedByDescending { it.activeSeconds }
            SortOption.SHORTEST -> searchFiltered.sortedBy { it.activeSeconds }
        }.sortedByDescending { it.isPinned }
    }

    var folderBeingEditedId by remember { mutableStateOf<String?>(null) }
    var isCreateFolderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderDescription by remember { mutableStateOf("") }
    var newFolderColor by remember { mutableStateOf(Color(0xFF4FA8F9)) }
    var folderOptionsId by remember { mutableStateOf<String?>(null) }

    val customColors by settingsRepository.customColorsFlow.collectAsState(initial = emptyList())

    val heatmapData by viewModel.heatmapData.collectAsState()

    val enableBlur by settingsRepository.enableBackgroundBlurFlow.collectAsState(initial = true)

    val sessionPendingDelete by viewModel.sessionPendingDelete.collectAsState()
    val descendantCount by viewModel.descendantCount.collectAsState()
    val streaks by viewModel.streaks.collectAsState()
    val selectedDailySummary by viewModel.selectedDailySummary.collectAsState()

    // selectedTab (0 = Timenotes, 1 = Folders) is hoisted into App.kt so it survives navigation

    val isPopupOpen = folderBeingEditedId != null || sessionPendingDelete != null || selectedDailySummary != null

    val blurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (enableBlur && isPopupOpen) 16.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "HistoryBlur"
    )

    val enableHaptics by settingsRepository.enableHapticsFlow.collectAsState(initial = true)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .blur(radius = blurRadius)
            .padding(24.dp)
    ) {

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = isSearchActive,
            label = "SearchBarAnimation"
        ) { targetIsSearchActive ->

        if (targetIsSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search sessions, notes, tags...", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                trailingIcon = {
                    IconButton(onClick = {
                        isSearchActive = false
                        viewModel.updateSearchQuery("")
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Search", tint = TextSecondary)
                    }
                },
                singleLine = true
            )
        } else {
            // Custom Segmented Control (Tabs) and Trash Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceDark)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(if (selectedTab == 0) Color(0xFF2C2C2C) else Color.Transparent)
                            .clickable {
                                if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // <-- ADD THIS
                                onTabSelected(0)
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sessions",
                            color = if (selectedTab == 0) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(if (selectedTab == 1) Color(0xFF2C2C2C) else Color.Transparent)
                            .clickable {
                                if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // <-- ADD THIS
                                onTabSelected(1)
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Folders",
                            color = if (selectedTab == 1) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Unified Action Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceDark)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(TextSecondary.copy(alpha = 0.3f)))

                    // Graph
                    IconButton(
                        onClick = onGraphClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Graph View", tint = DefaultAccentColor, modifier = Modifier.size(18.dp))
                    }

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(TextSecondary.copy(alpha = 0.3f)))

                    // Settings
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(TextSecondary.copy(alpha = 0.3f)))

                    // Trash
                    IconButton(
                        onClick = onTrashClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Recently Deleted", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (selectedTab == 0) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .height(IntrinsicSize.Min), // Forces the dividers to match the row height!
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Button 1: Calendar
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isCalendarView) DefaultAccentColor.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                isCalendarView = !isCalendarView
                                if (isCalendarView) coroutineScope.launch { listState.animateScrollToItem(0) } else selectedDate = null
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Calendar",
                            tint = if (isCalendarView) DefaultAccentColor else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Heatmap",
                            color = if (isCalendarView) DefaultAccentColor else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Divider 1
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(TextSecondary.copy(alpha = 0.2f)))

                    // Button 2: Tags
                    val hasTags = selectedFilterTags.isNotEmpty()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (hasTags) DefaultAccentColor.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { isTagFilterSheetOpen = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = "Tags",
                            tint = if (hasTags) DefaultAccentColor else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasTags) "${selectedFilterTags.size} Tags" else "Tags",
                            color = if (hasTags) DefaultAccentColor else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Divider 2
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight(0.6f).background(TextSecondary.copy(alpha = 0.2f)))

                    // Button 3: Sort
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { isSortSheetOpen = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Sort",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sortOption.displayName.split(" ").first(), // e.g., turns "Newest First" into "Newest" to fit perfectly!
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val fadeBrush = remember {
                    Brush.verticalGradient(
                        0f to Color.Transparent, // Top is invisible
                        0.02f to Color.Black,    // Fades to solid quickly
                        0.98f to Color.Black,    // Stays solid until the bottom
                        1f to Color.Transparent  // Bottom is invisible
                    )
                }

                LaunchedEffect(sortOption, selectedFilterTags, isCalendarView) {
                    if (finalDisplaySessions.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // THE FIX: Apply the fading edge mask!
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                        }
                ) {
                            item {
                                AnimatedVisibility(
                                    visible = isCalendarView,
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    FlowHeatmap(
                                        heatmapData = heatmapData, // THE FIX: Correct parameter name
                                        selectedDate = selectedDate, // THE FIX: Pass the missing parameter
                                        streaks = streaks,
                                        onDateSelected = { clickedDate ->
                                            viewModel.selectDateForSummary(clickedDate)
                                        }
                                    )
                                }
                            }

                    if (finalDisplaySessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No sessions recorded on this date.",
                                    color = TextSecondary,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    items(finalDisplaySessions, key = { it.id }) { session ->
                        val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.requestDelete(session) // <-- THE NEW INTERCEPTOR
                                    false // Bounce back
                                } else false
                            }
                        )


                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFE53935))
                                        .padding(end = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                }
                            },
                            content = {
                                SessionCard(session = session, allSessions = recentSessions, onClick = { onTimenoteClick(session.id) })
                            }
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            newFolderName = ""
                            newFolderDescription = ""
                            newFolderColor = Color(0xFF4FA8F9)
                            folderBeingEditedId = null
                            isCreateFolderDialogOpen = true
                        }
                        .drawBehind {
                            drawRoundRect(
                                color = TextSecondary.copy(alpha = 0.5f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                ),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ New Folder",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val sortedFolders = folders.sortedByDescending { it.isPinned }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sortedFolders) { folder ->
                        FolderCard(
                            folder = folder,
                            onClick = { onFolderClick(folder.id) },
                            onOptionsClick = {
                                folderOptionsId = folder.id
                            }
                        )
                    }
                }
            }
        }
    }

    if (isCreateFolderDialogOpen) {
        FolderEditorSheet(
            isEditing = folderBeingEditedId != null,
            name = newFolderName,
            onNameChange = { newFolderName = it },
            description = newFolderDescription,
            onDescriptionChange = { newFolderDescription = it },
            selectedColor = newFolderColor,
            onColorChange = { newFolderColor = it },
            customColors = customColors,
            onDismiss = { isCreateFolderDialogOpen = false },
            onSave = {
                viewModel.saveFolder(id = folderBeingEditedId, name = newFolderName, description = newFolderDescription, color = newFolderColor)
                isCreateFolderDialogOpen = false
                newFolderName = ""
                newFolderDescription = ""
                newFolderColor = Color(0xFF4FA8F9)
                folderBeingEditedId = null
            }
        )
    }

    val optionsFolder = folders.find { it.id == folderOptionsId }
    if (optionsFolder != null) {
        FolderOptionsSheet(
            folder = optionsFolder,
            onDismiss = { folderOptionsId = null },
            onTogglePin = {
                viewModel.toggleFolderPin(optionsFolder.id)
                folderOptionsId = null
            },
            onDelete = {
                viewModel.deleteFolder(optionsFolder.id)
                folderOptionsId = null
            }
        )
    }

    if (isSortSheetOpen) {
        SortSheet(
            current = sortOption,
            onSelect = {
                viewModel.setSortOption(it)
                isSortSheetOpen = false
            },
            onDismiss = { isSortSheetOpen = false }
        )
    }

    if (isTagFilterSheetOpen) {
        TagFilterSheet(
            allTags = allTags,
            selected = selectedFilterTags,
            onToggle = { viewModel.toggleFilterTag(it) },
            onClear = { viewModel.clearTagFilters() },
            onDismiss = { isTagFilterSheetOpen = false }
        )
    }

    if (sessionPendingDelete != null) {
        DeleteBranchSheet(
            descendantCount = descendantCount,
            onDeleteAll = { viewModel.confirmDelete(cascade = true) },
            onDeleteOnly = { viewModel.confirmDelete(cascade = false) },
            onCancel = { viewModel.cancelDelete() }
        )
    }

    selectedDailySummary?.let { summary ->
        DailySummarySheet(summary = summary, onDismiss = { viewModel.closeDailySummary() })
    }
}
