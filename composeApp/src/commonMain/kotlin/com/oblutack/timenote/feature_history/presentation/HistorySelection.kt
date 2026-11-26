package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oblutack.timenote.feature_history.domain.TagCoverage
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType

/** "1 selected", "12 selected". */
fun selectedCountLabel(count: Int): String = "$count selected"

/** Replaces the top bar while several sessions are selected: the count and Select all, and the four actions below. */
@Composable
fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    allPinned: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPin: () -> Unit,
    onFolder: () -> Unit,
    onTags: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = TextPrimary) }
            Text(selectedCountLabel(count), color = TextPrimary, fontSize = TnType.BodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!allSelected) TextButton(onClick = onSelectAll) { Text("Select all", color = TnTheme.colors.accent) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(TnShape.Medium).background(SurfaceDark).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            SelectionAction(Icons.Default.PushPin, if (allPinned) "Unpin" else "Pin", TextPrimary, onPin)
            SelectionAction(Icons.Default.Folder, "Move", TextPrimary, onFolder)
            SelectionAction(Icons.Default.Label, "Tags", TextPrimary, onTags)
            SelectionAction(Icons.Default.Delete, "Delete", TnTheme.colors.danger, onDelete)
        }
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(TnShape.Small).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = TnType.Caption)
    }
}

/** Tap a tag to put it on every selected session, or to take it off them all when they all have it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkTagSheet(
    allTags: List<TimenoteFolder>,
    coverage: (TimenoteFolder) -> TagCoverage,
    selectedCount: Int,
    onToggle: (TimenoteFolder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 48.dp)) {
            Text("Tags for $selectedCount sessions", color = TextPrimary, fontSize = TnType.Title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Tap a tag to add it to all of them, or to remove it when they all have it.", color = TextSecondary, fontSize = TnType.Caption)
            Spacer(modifier = Modifier.height(12.dp))
            if (allTags.isEmpty()) {
                Text("No tags yet. Create tags from the timer or a session's details.", color = TextSecondary, fontSize = TnType.Body)
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(allTags) { tag ->
                    val state = coverage(tag)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TnShape.Small)
                            .clickable { onToggle(tag) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(tag.color))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(tag.name, color = TextPrimary, fontSize = TnType.BodyLarge)
                        }
                        when (state) {
                            TagCoverage.All -> Icon(Icons.Default.CheckCircle, contentDescription = "On all", tint = tag.color)
                            TagCoverage.Some -> Icon(Icons.Default.RemoveCircleOutline, contentDescription = "On some", tint = tag.color)
                            TagCoverage.None -> Unit
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TnTheme.colors.accent)
            ) { Text("Done", color = TnTheme.colors.onAccent) }
        }
    }
}

/** Asked when some selected session has branches that are not selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteSelectedSheet(
    selectedCount: Int,
    branchCount: Int,
    onDeleteAll: () -> Unit,
    onDeleteOnly: () -> Unit,
    onCancel: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onCancel, containerColor = SurfaceDark) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = TnTheme.colors.danger, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Delete $selectedCount sessions?", color = TextPrimary, fontSize = TnType.Title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (branchCount == 1) "1 more session branches from them." else "$branchCount more sessions branch from them.",
                color = TextSecondary,
                fontSize = TnType.Body,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onDeleteAll,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TnTheme.colors.danger)
            ) { Text("Delete all ${selectedCount + branchCount} sessions", color = Color.White) }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDeleteOnly,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, TnTheme.colors.danger),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TnTheme.colors.danger)
            ) { Text("Delete just the $selectedCount selected") }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onCancel) { Text("Cancel", color = TextSecondary) }
        }
    }
}
