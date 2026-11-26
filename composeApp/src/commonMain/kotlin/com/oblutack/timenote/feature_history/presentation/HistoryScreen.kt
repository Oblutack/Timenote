package com.oblutack.timenote.feature_history.presentation

import com.oblutack.timenote.feature_history.domain.tagCoverage
import com.oblutack.timenote.feature_history.domain.filterSessions
import com.oblutack.timenote.feature_history.domain.DateFilter
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.groupSessionsByDay
import com.oblutack.timenote.feature_history.domain.HistoryDensity
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Folder
import com.oblutack.timenote.ui.components.EmptyState
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.Palette
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
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
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oblutack.timenote.ui.theme.BackgroundDark
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import androidx.compose.material3.IconButton
import kotlinx.datetime.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountTree
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
    onStartSessionClick: () -> Unit,
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
    var currentMonth by remember { mutableStateOf(today) }

    val searchQuery by viewModel.searchQuery.collectAsState()

    val selectedFilterTags by viewModel.selectedFilterTags.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val allTags by viewModel.tags.collectAsState(initial = emptyList())

    var isSortSheetOpen by remember { mutableStateOf(false) }
    var isTagFilterSheetOpen by remember { mutableStateOf(false) }
    var isDateSheetOpen by remember { mutableStateOf(false) }
    var isFolderSheetOpen by remember { mutableStateOf(false) }

    // --- Apply Filters and Sort ---
    val extraFilter by viewModel.filter.collectAsState()
    val filter = extraFilter.copy(tagIds = selectedFilterTags)
    // A day picked on the heatmap
    val selectedDate = (filter.date as? DateFilter.Day)?.date
    val filtered = remember(recentSessions, filter, searchQuery, today) {
        filterSessions(recentSessions, filter, searchQuery, today, TimeZone.currentSystemDefault(), getCurrentTimeMillis())
    }

    val finalDisplaySessions = remember(filtered, sortOption) {
        when (sortOption) {
            // THE FIX: Explicitly cast to Long to ensure perfect mathematical sorting
            SortOption.NEWEST -> filtered.sortedByDescending { it.createdAt.toLong() }
            SortOption.OLDEST -> filtered.sortedBy { it.createdAt.toLong() }
            SortOption.LONGEST -> filtered.sortedByDescending { it.activeSeconds }
            SortOption.SHORTEST -> filtered.sortedBy { it.activeSeconds }
        }.sortedByDescending { it.isPinned }
    }

    // Compact rows or large cards (remembered), and the sessions grouped by day when sorted by date
    val density by settingsRepository.historyDensityFlow.collectAsState(initial = HistoryDensity.Compact)
    val untaggedColor = TnTheme.colors.untagged
    val dayGroups = remember(finalDisplaySessions, sortOption, today, untaggedColor) {
        if (sortOption == SortOption.NEWEST || sortOption == SortOption.OLDEST) {
            groupSessionsByDay(finalDisplaySessions, today, TimeZone.currentSystemDefault(), getCurrentTimeMillis(), untaggedColor)
        } else emptyList()
    }

    var folderBeingEditedId by remember { mutableStateOf<String?>(null) }
    var isCreateFolderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderDescription by remember { mutableStateOf("") }
    var newFolderColor by remember { mutableStateOf(Palette.Blue) }
    var folderOptionsId by remember { mutableStateOf<String?>(null) }

    val customColors by settingsRepository.customColorsFlow.collectAsState(initial = emptyList())

    val heatmapData by viewModel.heatmapData.collectAsState()

    val enableBlur by settingsRepository.enableBackgroundBlurFlow.collectAsState(initial = true)

    val sessionPendingDelete by viewModel.sessionPendingDelete.collectAsState()
    val descendantCount by viewModel.descendantCount.collectAsState()

    // Several sessions selected (long-press): only the ones that still exist count
    val rawSelection by viewModel.selectedIds.collectAsState()
    val selectedSessions = remember(rawSelection, recentSessions) { recentSessions.filter { it.id in rawSelection } }
    val selecting = selectedTab == 0 && selectedSessions.isNotEmpty()
    val bulkPendingDelete by viewModel.bulkPendingDelete.collectAsState()
    var isMoveSheetOpen by remember { mutableStateOf(false) }
    var isBulkTagSheetOpen by remember { mutableStateOf(false) }
    com.oblutack.timenote.ui.components.BackPress(enabled = selecting) { viewModel.clearSelection() }
    val streaks by viewModel.streaks.collectAsState()
    val selectedDailySummary by viewModel.selectedDailySummary.collectAsState()
    val backupReminder by viewModel.backupReminder.collectAsState()

    // selectedTab (0 = Timenotes, 1 = Folders) is hoisted into App.kt so it survives navigation

    val isPopupOpen = folderBeingEditedId != null || sessionPendingDelete != null || bulkPendingDelete != null || selectedDailySummary != null

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

        if (selecting) {
            SelectionBar(
                count = selectedSessions.size,
                allSelected = selectedSessions.size == finalDisplaySessions.size,
                allPinned = selectedSessions.none { !it.isPinned },
                onClose = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAll(finalDisplaySessions.map { it.id }) },
                onPin = { viewModel.pinSelected() },
                onFolder = { isMoveSheetOpen = true },
                onTags = { isBulkTagSheetOpen = true },
                onDelete = { viewModel.requestDeleteSelected() }
            )
        } else HistoryTopBar(
            selectedTab = selectedTab,
            onTabSelected = {
                if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                viewModel.clearSelection()
                onTabSelected(it)
            },
            onSettingsClick = onSettingsClick,
            onGraphClick = onGraphClick,
            onTrashClick = onTrashClick,
            sortLabel = sortOption.displayName.split(" ").first(),
            onSortClick = { isSortSheetOpen = true },
            density = density,
            onToggleDensity = {
                val next = if (density == HistoryDensity.Compact) HistoryDensity.Comfortable else HistoryDensity.Compact
                coroutineScope.launch { settingsRepository.setHistoryDensity(next) }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (selectedTab == 0) {
            Column(modifier = Modifier.fillMaxSize()) {
                HistorySearchField(query = searchQuery, onQueryChange = { viewModel.updateSearchQuery(it) })
                Spacer(modifier = Modifier.height(10.dp))
                HistoryFilterChips(
                    filter = filter,
                    folderName = folders.find { it.id == filter.folderId }?.name,
                    searchActive = searchQuery.isNotBlank(),
                    onDate = { isDateSheetOpen = true },
                    onFolder = { isFolderSheetOpen = true },
                    onTags = { isTagFilterSheetOpen = true },
                    onToggleNotes = { viewModel.toggleHasNotes() },
                    onToggleVoice = { viewModel.toggleHasVoice() },
                    onToggleBranches = { viewModel.toggleHasBranches() },
                    onTogglePinned = { viewModel.togglePinnedOnly() },
                    onClearAll = { isCalendarView = false; viewModel.clearAllFilters() }
                )

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
                    verticalArrangement = Arrangement.spacedBy(if (density == HistoryDensity.Compact) 6.dp else 16.dp),
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
                            // A quiet reminder (only when sync is off and the last backup is old)
                            val reminder = backupReminder
                            if (reminder != null) {
                                item(key = "backup_reminder") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(TnShape.Card)
                                            .background(DefaultAccentColor.copy(alpha = 0.12f))
                                            .padding(16.dp)
                                    ) {
                                        Text("Back up your timenotes", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(reminder, color = TextSecondary, fontSize = TnType.Label)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row {
                                            TextButton(onClick = onSettingsClick) { Text("Back up now", color = DefaultAccentColor) }
                                            TextButton(onClick = { viewModel.dismissBackupReminder() }) { Text("Not now", color = TextSecondary) }
                                        }
                                    }
                                }
                            }

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
                                            // Tapping a day filters the list to it; tapping it again clears the filter
                                            if (selectedDate == clickedDate) {
                                                viewModel.setDateFilter(DateFilter.Any)
                                            } else {
                                                viewModel.setDateFilter(DateFilter.Day(clickedDate))
                                                viewModel.selectDateForSummary(clickedDate)
                                            }
                                        }
                                    )
                                }
                            }

                    if (finalDisplaySessions.isEmpty()) {
                        item {
                            when (historyEmptyReason(dayPicked = selectedDate != null, hasSearchOrFilter = searchQuery.isNotBlank() || filter.isActive)) {
                                HistoryEmpty.NothingOnThatDay -> EmptyState(
                                    icon = Icons.Default.DateRange,
                                    title = "Nothing on this day",
                                    hint = "No session was recorded on this date.",
                                    actionLabel = "Show all days",
                                    onAction = { viewModel.setDateFilter(DateFilter.Any) }
                                )
                                HistoryEmpty.NoMatches -> EmptyState(
                                    icon = Icons.Default.Search,
                                    title = "No matches",
                                    hint = "Try other words, or fewer tags.",
                                    actionLabel = "Clear search and filters",
                                    onAction = { isCalendarView = false; viewModel.clearAllFilters() }
                                )
                                HistoryEmpty.NothingYet -> EmptyState(
                                    icon = Icons.Default.PlayArrow,
                                    title = "No sessions yet",
                                    hint = "Start one on the Timer. It appears here with its notes and tags.",
                                    actionLabel = "Go to the Timer",
                                    onAction = onStartSessionClick
                                )
                            }
                        }
                    }

                    val itemContent: @Composable (Timenote) -> Unit = { session ->
                        HistorySessionItem(
                            session = session,
                            allSessions = recentSessions,
                            folderName = folders.find { it.id == session.folderId }?.name,
                            density = density,
                            enableHaptics = enableHaptics,
                            onDelete = { viewModel.requestDelete(it) },
                            onTogglePin = { viewModel.togglePinOf(it) },
                            onClick = { if (selecting) viewModel.toggleSelected(session.id) else onTimenoteClick(session.id) },
                            onLongClick = {
                                if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleSelected(session.id)
                            },
                            selecting = selecting,
                            selected = session.id in rawSelection,
                            query = searchQuery
                        )
                    }
                    if (dayGroups.isNotEmpty()) {
                        dayGroups.forEach { group ->
                            item(key = "day_${group.date}_${group.title}") { DayHeader(group) }
                            items(group.sessions, key = { it.id }) { session -> itemContent(session) }
                        }
                    } else {
                        items(finalDisplaySessions, key = { it.id }) { session -> itemContent(session) }
                    }
                }
            }
        } else {
            val dashColor = TextSecondary
            val openNewFolder = {
                newFolderName = ""
                newFolderDescription = ""
                newFolderColor = Palette.Blue
                folderBeingEditedId = null
                isCreateFolderDialogOpen = true
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(TnShape.Card)
                        .clickable { openNewFolder() }
                        .drawBehind {
                            drawRoundRect(
                                color = dashColor.copy(alpha = 0.5f),
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
                        fontSize = TnType.Body,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val sortedFolders = folders.sortedByDescending { it.isPinned }

                if (sortedFolders.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Folder,
                        title = "No folders yet",
                        hint = "A folder groups the sessions of one project, course or habit.",
                        actionLabel = "Create a folder",
                        onAction = { openNewFolder() }
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sortedFolders) { folder ->
                        FolderCard(
                            folder = folder,
                            sessionCount = recentSessions.count { it.folderId == folder.id },
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
                newFolderColor = Palette.Blue
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

    if (isDateSheetOpen) {
        DateFilterSheet(
            current = filter.date,
            onSelect = {
                viewModel.setDateFilter(it)
                isCalendarView = false
                isDateSheetOpen = false
            },
            onPickDay = {
                // The heatmap opens above the list; tapping a day on it sets the filter
                isCalendarView = true
                isDateSheetOpen = false
            },
            onDismiss = { isDateSheetOpen = false }
        )
    }

    if (isFolderSheetOpen) {
        FolderFilterSheet(
            folders = folders,
            currentId = filter.folderId,
            onSelect = {
                viewModel.setFolderFilter(it)
                isFolderSheetOpen = false
            },
            onDismiss = { isFolderSheetOpen = false }
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

    if (isMoveSheetOpen && selecting) {
        FolderPickerSheet(
            folders = folders,
            currentFolderId = selectedSessions.firstNotNullOfOrNull { it.folderId },
            onAssign = {
                viewModel.moveSelectedToFolder(it)
                isMoveSheetOpen = false
            },
            onDismiss = { isMoveSheetOpen = false }
        )
    }

    if (isBulkTagSheetOpen) {
        if (selecting) {
            BulkTagSheet(
                allTags = allTags,
                coverage = { tag -> tagCoverage(selectedSessions, tag.id) },
                selectedCount = selectedSessions.size,
                onToggle = { viewModel.toggleTagOnSelected(it) },
                onDismiss = { isBulkTagSheetOpen = false; viewModel.clearSelection() }
            )
        } else isBulkTagSheetOpen = false
    }

    bulkPendingDelete?.let { pending ->
        DeleteSelectedSheet(
            selectedCount = pending.ids.size,
            branchCount = pending.branchCount,
            onDeleteAll = { viewModel.confirmDeleteSelected(cascade = true) },
            onDeleteOnly = { viewModel.confirmDeleteSelected(cascade = false) },
            onCancel = { viewModel.cancelDeleteSelected() }
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
