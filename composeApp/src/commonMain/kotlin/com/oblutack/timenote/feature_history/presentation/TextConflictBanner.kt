package com.oblutack.timenote.feature_history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.feature_history.domain.TextConflict
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Shown on a note when two devices changed its text at the same time. The newer text is in the note; the other one was
 * kept, and can be looked at, brought back (the replaced text then becomes the other version) or dismissed.
 */
@Composable
fun TextConflictBanner(
    conflicts: List<TextConflict>,
    onRestore: (textHash: String) -> Unit,
    onDismiss: (textHash: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (conflicts.isEmpty()) return
    var isOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            if (conflicts.size == 1) "Another version of this note's text was saved."
            else "${conflicts.size} other versions of this note's text were saved.",
            color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        TextButton(onClick = { isOpen = true }) { Text("View", color = DefaultAccentColor, fontWeight = FontWeight.Bold) }
    }

    if (isOpen) {
        AlertDialog(
            onDismissRequest = { isOpen = false },
            containerColor = SurfaceDark,
            title = { Text("Other versions", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "These texts were changed on another device at the same time. The newer one is in your note. " +
                            "Restoring one puts it in the note and keeps the current text here.",
                        color = TextSecondary, fontSize = 12.sp
                    )
                    conflicts.forEach { conflict ->
                        Column {
                            Text("Written ${formatWhen(conflict.writtenAt)}", color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                                Text(conflict.text, color = TextPrimary, fontSize = 14.sp)
                            }
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { onDismiss(conflict.textHash); if (conflicts.size == 1) isOpen = false }) {
                                    Text("Dismiss", color = TextSecondary)
                                }
                                TextButton(onClick = { onRestore(conflict.textHash); isOpen = false }) {
                                    Text("Restore", color = DefaultAccentColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { isOpen = false }) { Text("Close", color = DefaultAccentColor) } }
        )
    }
}

private fun formatWhen(epochMillis: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${t.dayOfMonth}.${t.monthNumber}.${t.year} at ${two(t.hour)}:${two(t.minute)}"
}
