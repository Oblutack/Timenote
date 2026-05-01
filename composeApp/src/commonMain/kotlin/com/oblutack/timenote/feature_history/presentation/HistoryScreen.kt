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

    var folderBeingEditedId by remember { mutableStateOf<String?>(null) }
    var isCreateFolderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderColor by remember { mutableStateOf(Color(0xFF4FA8F9)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {

        Spacer(modifier = Modifier.height(24.dp))

        // Custom Segmented Control (Tabs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
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

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedTab == 0) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark) // Matches your session cards
                        .clickable {
                            isCalendarView = !isCalendarView
                            if (isCalendarView) {
                                // Smoothly scroll to the top to reveal the newly opened calendar!
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                            } else {
                                selectedDate = null
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        // --- FIX: Use AutoMirrored.Filled for the ArrowBack ---
                        imageVector = if (isCalendarView) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.DateRange,
                        contentDescription = "Toggle Calendar",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCalendarView) "Close Calendar" else "Filter by Date",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
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

                    if (displaySessions.isEmpty()) {
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

                    items(displaySessions, key = { it.id }) { session ->
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
                                SessionCard(session = session, onClick = { onTimenoteClick(session.id) })
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

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(folders) { folder ->
                        FolderCard(
                            folder = folder,
                            onClick = { onFolderClick(folder.id) },
                            onEditClick = {
                                newFolderName = folder.name
                                newFolderColor = folder.color
                                folderBeingEditedId = folder.id
                                isCreateFolderDialogOpen = true
                            },
                            onDeleteClick = {
                                viewModel.deleteFolder(folder.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (isCreateFolderDialogOpen) {
        Dialog(onDismissRequest = { isCreateFolderDialogOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text("Create New Folder", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        Color(0xFF4FA8F9), Color(0xFF4CAF50), Color(0xFFFF9800),
                        Color(0xFF9C27B0), Color(0xFFE53935), Color(0xFF00BCD4)
                    ).forEach { color ->
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
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { isCreateFolderDialogOpen = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                viewModel.saveFolder(folderBeingEditedId, newFolderName, newFolderColor)
                                isCreateFolderDialogOpen = false
                                newFolderName = ""
                                newFolderColor = Color(0xFF4FA8F9)
                                folderBeingEditedId = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FA8F9), contentColor = Color.White)
                    ) {
                        Text("Save")
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
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var isMenuExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

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
            Text(
                text = "Folder",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Right Side: Massive 3-dot hit target (Takes up 25% of the card)
        Box(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxHeight()
                // Clicks on this exact area open the menu instead of the folder!
                .clickable { isMenuExpanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp) // Slightly larger icon to fit the big hit area
            )

            // The Dropdown Menu
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                DropdownMenuItem(
                    text = { Text("Edit", color = TextPrimary) },
                    onClick = {
                        isMenuExpanded = false
                        onEditClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFE53935)) }, // Red for danger
                    onClick = {
                        isMenuExpanded = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

@Composable
fun SessionCard(session: Timenote, onClick: () -> Unit) {
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
                Text(
                    text = session.description,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tags / Folders
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
