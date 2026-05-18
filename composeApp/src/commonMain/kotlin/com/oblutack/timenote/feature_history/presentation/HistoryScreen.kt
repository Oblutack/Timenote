package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import kotlinx.datetime.*
import kotlinx.coroutines.launch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PushPin

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
    viewModel: HistoryViewModel = viewModel { HistoryViewModel() }
) {
    val recentSessions by viewModel.sessions.collectAsState()
    val folders by viewModel.folders.collectAsState(initial = emptyList())

    var isCalendarView by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val today = remember {
        Instant.fromEpochMilliseconds(com.oblutack.timenote.getCurrentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var currentMonth by remember { mutableStateOf(today) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }

    val sessionsByDate = remember(recentSessions) {
        recentSessions.groupBy {
            Instant.fromEpochMilliseconds(if (it.createdAt > 0L) it.createdAt else com.oblutack.timenote.getCurrentTimeMillis())
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

    val customColors by com.oblutack.timenote.data.repository.SettingsRepository.customColorsFlow.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {

        Spacer(modifier = Modifier.height(24.dp))

        androidx.compose.animation.AnimatedContent(
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
                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close Search", tint = TextSecondary)
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
                            .clickable { onTabSelected(0) },

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
                            .clickable { onTabSelected(1) },

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
                        Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = "Search", tint = TextSecondary, modifier = Modifier.size(18.dp))
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
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Calendar",
                            tint = if (isCalendarView) DefaultAccentColor else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Calendar",
                            color = if (isCalendarView) DefaultAccentColor else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    if (isCalendarView) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = {
                                        val prevMonth = if (currentMonth.monthNumber == 1) 12 else currentMonth.monthNumber - 1
                                        val prevYear = if (currentMonth.monthNumber == 1) currentMonth.year - 1 else currentMonth.year
                                        currentMonth = LocalDate(prevYear, prevMonth, 1)
                                    }) {
                                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Month", tint = TextPrimary)
                                    }
                                    Text(
                                        text = "${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${currentMonth.year}",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = {
                                        val nextMonth = if (currentMonth.monthNumber == 12) 1 else currentMonth.monthNumber + 1
                                        val nextYear = if (currentMonth.monthNumber == 12) currentMonth.year + 1 else currentMonth.year
                                        currentMonth = LocalDate(nextYear, nextMonth, 1)
                                    }) {
                                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month", tint = TextPrimary)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                                        Text(day, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }

                                val startDayOfWeek = LocalDate(currentMonth.year, currentMonth.monthNumber, 1).dayOfWeek.isoDayNumber
                                val offset = startDayOfWeek - 1
                                val totalDays = getDaysInMonth(currentMonth.monthNumber, currentMonth.year)

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(7),
                                    modifier = Modifier.fillMaxWidth().height(280.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(offset) { Spacer(modifier = Modifier.aspectRatio(1f)) }
                                    items(totalDays) { dayIndex ->
                                        val day = dayIndex + 1
                                        val thisDate = LocalDate(currentMonth.year, currentMonth.monthNumber, day)
                                        val isSelected = selectedDate == thisDate
                                        val daySessions = sessionsByDate[thisDate]

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(CircleShape)
                                                .background(if (isSelected) SurfaceDark else Color.Transparent)
                                                .clickable { selectedDate = thisDate },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = day.toString(),
                                                    color = if (thisDate == today) DefaultAccentColor else TextPrimary,
                                                    fontSize = 16.sp
                                                )
                                                if (daySessions != null && daySessions.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(daySessions.first().tags.firstOrNull()?.color ?: DefaultAccentColor)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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

                    items(finalDisplaySessions, key = { "${it.id}_${it.hashCode()}" }) { session ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteTimenote(session.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        androidx.compose.runtime.LaunchedEffect(sortOption, selectedFilterTags, isCalendarView) {
                            if (finalDisplaySessions.isNotEmpty()) {
                                listState.animateScrollToItem(0)
                            }
                        }

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
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
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
        ModalBottomSheet(
            onDismissRequest = { isCreateFolderDialogOpen = false },
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                Text(
                    text = if (folderBeingEditedId == null) "Create New Folder" else "Edit Folder", // <--- DYNAMIC TITLE
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
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
                    value = newFolderDescription,
                    onValueChange = { newFolderDescription = it },
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
                                    if (color == newFolderColor) {
                                        it.border(2.dp, Color.White, CircleShape)
                                    } else it
                                }
                                .clickable { newFolderColor = color }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { isCreateFolderDialogOpen = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                viewModel.saveFolder(id = folderBeingEditedId, name = newFolderName, description = newFolderDescription, color = newFolderColor)
                                isCreateFolderDialogOpen = false
                                newFolderName = ""
                                newFolderDescription = ""
                                newFolderColor = Color(0xFF4FA8F9)
                                folderBeingEditedId = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (folderOptionsId != null) {
        val selectedFolder = folders.find { it.id == folderOptionsId }
        if (selectedFolder != null) {
            ModalBottomSheet(
                onDismissRequest = { folderOptionsId = null },
                containerColor = SurfaceDark
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp)
                ) {
                    Text(
                        text = selectedFolder.name,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleFolderPin(selectedFolder.id)
                                folderOptionsId = null
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.PushPin, contentDescription = if (selectedFolder.isPinned) "Unpin" else "Pin", tint = TextPrimary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = if (selectedFolder.isPinned) "Unpin Folder" else "Pin Folder", color = TextPrimary, fontSize = 16.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.deleteFolder(selectedFolder.id)
                                folderOptionsId = null
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
    }
    if (isSortSheetOpen) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { isSortSheetOpen = false },
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp)) {
                Text("Sort By", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(16.dp))
                SortOption.entries.forEach { option ->
                    val isSelected = option == sortOption
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setSortOption(option); isSortSheetOpen = false }.padding(horizontal = 24.dp, vertical = 16.dp),
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

    if (isTagFilterSheetOpen) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { isTagFilterSheetOpen = false },
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter by Tags", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.TextButton(onClick = { viewModel.clearTagFilters() }) { Text("Clear All", color = TextSecondary) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(allTags, key = { "${it.id}_${it.hashCode()}" }) { tag ->
                        val isSelected = selectedFilterTags.contains(tag.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleFilterTag(tag.id) }.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(tag.color))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(tag.name, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Checkbox(
                                checked = isSelected, onCheckedChange = { viewModel.toggleFilterTag(tag.id) },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = DefaultAccentColor, uncheckedColor = TextSecondary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderCard(
    folder: com.oblutack.timenote.feature_history.domain.ProjectFolder,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            // --- NEW: The thin color-coded border ---
            .border(1.dp, folder.color, RoundedCornerShape(16.dp))
            .clickable { onClick() }, // Left side (75%) clicks the folder
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Text Content (Takes up 75% of the card)
        Column(
            modifier = Modifier
                .weight(0.75f)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = folder.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (folder.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = DefaultAccentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = folder.description?.takeIf { it.isNotBlank() } ?: "Folder",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // Right Side: Massive 3-dot hit target (Takes up 25% of the card)
        Box(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxHeight()
                // Clicks on this exact area open the options
                .clickable { onOptionsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp) // Slightly larger icon to fit the big hit area
            )
        }
    }
}

@Composable
fun SessionCard(
    session: com.oblutack.timenote.feature_history.domain.Timenote,
    allSessions: List<com.oblutack.timenote.feature_history.domain.Timenote>,
    onClick: () -> Unit
) {
    val childCount = allSessions.count { it.parentTimenoteId == session.id }
    val isChild = session.parentTimenoteId != null

    // 1. Calculate Date and Year strings
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(
        if (session.createdAt > 0L) session.createdAt else com.oblutack.timenote.getCurrentTimeMillis()
    )
    val dateTime = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val dateString = "$month ${dateTime.dayOfMonth}"
    val yearString = "${dateTime.year}"

    val isLegacyDesc = session.description.contains("waypoints recorded")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top // Align everything to the top
    ) {
        // --- LEFT COLUMN (Takes up remaining space, pushes away from Right Column) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp) // Keeps the description from touching the dates
        ) {
            Text(
                text = session.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            // Description (Capped at 2 lines, respects the weight bounds)
            if (session.description.isNotBlank() && !isLegacyDesc) {
                Spacer(modifier = Modifier.height(8.dp))
                val dynamicAccentColor = session.tags.firstOrNull()?.color ?: DefaultAccentColor
                Text(
                    text = com.oblutack.timenote.core.parseMarkdownToAnnotatedString(session.description, dynamicAccentColor),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tags / Folders
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isChild) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, Color(0xFF9C27B0), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "↳ Child",
                            color = Color(0xFF9C27B0),
                            fontSize = 12.sp
                        )
                    }
                }
                
                if (childCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Branches: $childCount",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                }

                session.tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, tag.color, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tag.name,
                            color = tag.color,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // --- RIGHT COLUMN (Duration, Date, Year) ---
        Column(
            horizontalAlignment = Alignment.End // Right-aligns all the text
        ) {
            if (session.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = DefaultAccentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = session.duration,
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateString,
                color = TextSecondary.copy(alpha = 0.7f), // Make date slightly dimmer
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = yearString,
                color = TextSecondary.copy(alpha = 0.5f), // Make year even dimmer for hierarchy
                fontSize = 12.sp
            )
        }
    }
}
