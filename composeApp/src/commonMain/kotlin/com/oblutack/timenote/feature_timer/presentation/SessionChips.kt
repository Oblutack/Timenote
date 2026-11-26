package com.oblutack.timenote.feature_timer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType

/** How many tag chips fit in the line before the rest are counted ("+2"). */
const val SESSION_CHIPS_MAX_TAGS = 3

/**
 * What the chip line says about the tags: the names of the first few selected tags, then how many more.
 * Pure so it can be tested; the colours are looked up by the caller.
 */
fun visibleTagChips(selectedCount: Int): Pair<Int, Int> {
    val shown = minOf(selectedCount, SESSION_CHIPS_MAX_TAGS)
    return shown to (selectedCount - shown)
}

/**
 * The folder and tags of the session, in one line above the controls: a folder chip (tap to choose), the selected tags,
 * and a chip that opens the full tag list. While idle they come filled in from the last session.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionChips(state: TimerState, onAction: (TimerAction) -> Unit) {
    var folderMenuOpen by remember { mutableStateOf(false) }
    val folder = state.selectedFolder
    val (shown, hidden) = visibleTagChips(state.selectedCategories.size)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The folder
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(TnShape.Pill)
                    .background(if (folder != null) folder.color.copy(alpha = 0.2f) else Color.Transparent)
                    .border(1.dp, folder?.color ?: TextSecondary.copy(alpha = 0.5f), TnShape.Pill)
                    .clickable { folderMenuOpen = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (folder != null) {
                    Box(modifier = Modifier.size(8.dp).background(folder.color, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = folder?.name ?: "No folder",
                    color = if (folder != null) TextPrimary else TextSecondary,
                    fontSize = TnType.Caption,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }, containerColor = SurfaceDark) {
                DropdownMenuItem(
                    text = { Text("No folder", color = TextSecondary) },
                    onClick = { onAction(TimerAction.SelectFolder(null)); folderMenuOpen = false }
                )
                state.availableFolders.forEach { f ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(f.color, CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(f.name, color = TextPrimary)
                            }
                        },
                        onClick = {
                            // the action toggles when the same folder is tapped again; "pick" must not clear it
                            if (state.selectedFolder?.id != f.id) onAction(TimerAction.SelectFolder(f))
                            folderMenuOpen = false
                        }
                    )
                }
            }
        }

        // The selected tags (tapping one opens the full list)
        state.selectedCategories.take(shown).forEach { tag ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(TnShape.Pill)
                    .background(tag.color.copy(alpha = 0.2f))
                    .border(1.dp, tag.color, TnShape.Pill)
                    .clickable { onAction(TimerAction.ToggleTagsRowVisibility) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(tag.color, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(tag.name, color = TextPrimary, fontSize = TnType.Caption, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                color = TextSecondary,
                fontSize = TnType.Caption,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterVertically).clickable { onAction(TimerAction.ToggleTagsRowVisibility) }.padding(4.dp)
            )
        }

        // Opens and closes the full tag list
        Box(
            modifier = Modifier
                .clip(TnShape.Pill)
                .background(if (state.selectedCategories.isNotEmpty()) TnTheme.colors.surface else Color.Transparent)
                .border(1.dp, TextSecondary.copy(alpha = 0.5f), TnShape.Pill)
                .clickable { onAction(TimerAction.ToggleTagsRowVisibility) }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = when {
                    state.isTagsRowVisible -> "Done"
                    state.selectedCategories.isEmpty() -> "+ Tags"
                    else -> "+"
                },
                color = TextSecondary,
                fontSize = TnType.Caption,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
