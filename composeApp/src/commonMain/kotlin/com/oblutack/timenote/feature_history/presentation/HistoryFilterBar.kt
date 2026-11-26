package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oblutack.timenote.feature_history.domain.DateFilter
import com.oblutack.timenote.feature_history.domain.HistoryDensity
import com.oblutack.timenote.feature_history.domain.HistoryFilter
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType

/** The words on the Date chip. */
fun dateFilterLabel(filter: DateFilter): String = when (filter) {
    DateFilter.Any -> "Date"
    DateFilter.Today -> "Today"
    DateFilter.Last7Days -> "Last 7 days"
    DateFilter.Last30Days -> "Last 30 days"
    DateFilter.ThisMonth -> "This month"
    is DateFilter.Day -> {
        val month = filter.date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        "$month ${filter.date.dayOfMonth}"
    }
}

/** Top row of the History screen: the Sessions / Folders tabs, Settings, and a menu with the rest. */
@Composable
fun HistoryTopBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onGraphClick: () -> Unit,
    onTrashClick: () -> Unit,
    sortLabel: String,
    onSortClick: () -> Unit,
    density: HistoryDensity,
    onToggleDensity: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().clip(TnShape.Pill).background(SurfaceDark).padding(4.dp)
        ) {
            listOf("Sessions", "Folders").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(TnShape.Pill)
                        .background(if (selectedTab == index) TnTheme.colors.surfaceRaised else Color.Transparent)
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selectedTab == index) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = TnType.Body
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = SurfaceDark) {
                DropdownMenuItem(text = { Text("Graph", color = TextPrimary) }, onClick = { menuOpen = false; onGraphClick() })
                DropdownMenuItem(text = { Text("Recently deleted", color = TextPrimary) }, onClick = { menuOpen = false; onTrashClick() })
                DropdownMenuItem(text = { Text("Sort: $sortLabel", color = TextPrimary) }, onClick = { menuOpen = false; onSortClick() })
                DropdownMenuItem(
                    text = { Text(if (density == HistoryDensity.Compact) "Show large cards" else "Show compact rows", color = TextPrimary) },
                    onClick = { menuOpen = false; onToggleDensity() }
                )
            }
        }
    }
}

/** The search field, always visible; the cross takes the words away. */
@Composable
fun HistorySearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search sessions, notes, tags...", color = TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = TnShape.Pill,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceDark,
            unfocusedContainerColor = SurfaceDark,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        singleLine = true
    )
}

/** One chip of the filter bar: highlighted while its filter is on. */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(TnShape.Pill)
            .background(if (selected) DefaultAccentColor.copy(alpha = 0.15f) else SurfaceDark)
            .border(1.dp, if (selected) DefaultAccentColor else TextSecondary.copy(alpha = 0.3f), TnShape.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) DefaultAccentColor else TextSecondary,
            fontSize = TnType.Label,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The chips under the search field: Date, Folder, Tags, what a session contains, Pinned, and Clear when anything is on. */
@Composable
fun HistoryFilterChips(
    filter: HistoryFilter,
    folderName: String?,
    searchActive: Boolean,
    onDate: () -> Unit,
    onFolder: () -> Unit,
    onTags: () -> Unit,
    onToggleNotes: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleBranches: () -> Unit,
    onTogglePinned: () -> Unit,
    onClearAll: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        item { FilterPill(dateFilterLabel(filter.date), filter.date != DateFilter.Any, onDate) }
        item { FilterPill(folderName ?: "Folder", filter.folderId != null, onFolder) }
        item { FilterPill(if (filter.tagIds.isEmpty()) "Tags" else if (filter.tagIds.size == 1) "1 tag" else "${filter.tagIds.size} tags", filter.tagIds.isNotEmpty(), onTags) }
        item { FilterPill("Notes", filter.hasNotes, onToggleNotes) }
        item { FilterPill("Voice", filter.hasVoice, onToggleVoice) }
        item { FilterPill("Branches", filter.hasBranches, onToggleBranches) }
        item { FilterPill("Pinned", filter.pinnedOnly, onTogglePinned) }
        if (filter.isActive || searchActive) {
            item {
                Box(
                    modifier = Modifier.clip(TnShape.Pill).clickable(onClick = onClearAll).padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Clear all", color = TextSecondary, fontSize = TnType.Label, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Which days: quick ranges, or one day picked on the heatmap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterSheet(current: DateFilter, onSelect: (DateFilter) -> Unit, onPickDay: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 48.dp)) {
            Text("Date", color = TextPrimary, fontSize = TnType.Title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            val options = listOf(
                "Any time" to DateFilter.Any,
                "Today" to DateFilter.Today,
                "Last 7 days" to DateFilter.Last7Days,
                "Last 30 days" to DateFilter.Last30Days,
                "This month" to DateFilter.ThisMonth
            )
            options.forEach { (label, value) ->
                SheetOption(label = label, selected = current == value) { onSelect(value) }
            }
            SheetOption(label = "Pick a day on the heatmap", selected = current is DateFilter.Day, onClick = onPickDay)
        }
    }
}

/** Which folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderFilterSheet(folders: List<ProjectFolder>, currentId: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 48.dp)) {
            Text("Folder", color = TextPrimary, fontSize = TnType.Title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            SheetOption(label = "Any folder", selected = currentId == null) { onSelect(null) }
            folders.forEach { folder ->
                SheetOption(label = folder.name, selected = currentId == folder.id, dot = folder.color) { onSelect(folder.id) }
            }
            if (folders.isEmpty()) {
                Text("No folders yet.", color = TextSecondary, fontSize = TnType.Body, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun SheetOption(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TnShape.Small)
            .background(if (selected) DefaultAccentColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(modifier = Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot))
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(label, color = if (selected) DefaultAccentColor else TextPrimary, fontSize = TnType.BodyLarge)
    }
}
