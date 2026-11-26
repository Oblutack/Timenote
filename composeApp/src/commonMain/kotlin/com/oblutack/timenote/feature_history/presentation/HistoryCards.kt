package com.oblutack.timenote.feature_history.presentation
import androidx.compose.foundation.combinedClickable
import com.oblutack.timenote.feature_history.domain.identityColor
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.setValue
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.ProjectFolder
import androidx.compose.material.icons.filled.List
import kotlinx.datetime.*
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.ui.text.style.TextOverflow
import com.oblutack.timenote.getCurrentTimeMillis
/** More tags than this on a session card are summarised as "+N". */
private const val MAX_TAGS_ON_CARD = 3

@Composable
fun FolderCard(
    folder: ProjectFolder,
    sessionCount: Int,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(TnShape.Card)
            .background(SurfaceDark)
            // --- NEW: The thin color-coded border ---
            .border(1.dp, folder.color, TnShape.Card)
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
                fontSize = TnType.BodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                    text = folder.description?.takeIf { it.isNotBlank() } ?: if (sessionCount == 1) "1 session" else "$sessionCount sessions",
                    color = TextSecondary,
                    fontSize = TnType.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionCard(
    session: Timenote,
    allSessions: List<Timenote>,
    onClick: () -> Unit,
    query: String = "",
    onLongClick: (() -> Unit)? = null
) {
    val accent = com.oblutack.timenote.ui.theme.TnTheme.colors.accent
    val snippet = com.oblutack.timenote.feature_history.domain.searchSnippet(session, query)
    val childCount = allSessions.count { it.parentTimenoteId == session.id }
    val isChild = session.parentTimenoteId != null

    // 1. Calculate Date and Year strings
    val instant = Instant.fromEpochMilliseconds(
        if (session.createdAt > 0L) session.createdAt else getCurrentTimeMillis()
    )
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val dateString = "$month ${dateTime.dayOfMonth}"
    val yearString = "${dateTime.year}"

    val isLegacyDesc = session.description.contains("waypoints recorded")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TnShape.Card)
            .background(SurfaceDark)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                text = highlighted(session.title, com.oblutack.timenote.feature_history.domain.findMatchRanges(session.title, query), accent),
                color = TextPrimary,
                fontSize = TnType.BodyLarge,
                fontWeight = FontWeight.Medium
            )

            // The line that matched the search, or else a plain-words preview of the description (2 lines at most):
            // no markdown symbols and no big headings in a list
            if (snippet != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = highlighted(snippet.text, snippet.ranges, accent),
                    color = TextSecondary,
                    fontSize = TnType.Body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (session.description.isNotBlank() && !isLegacyDesc) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = com.oblutack.timenote.core.plainText(session.description),
                    color = TextSecondary,
                    fontSize = TnType.Body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tags / Folders
            // Wraps onto a second line instead of running off the card
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isChild) {
                    Box(
                        modifier = Modifier
                            .clip(TnShape.Pill)
                            .border(1.dp, TnTheme.colors.branch, TnShape.Pill)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "↳ Child",
                            color = TnTheme.colors.branch,
                            fontSize = TnType.Caption
                        )
                    }
                }
                
                if (childCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(TnShape.Pill)
                            .border(1.dp, TnTheme.colors.success, TnShape.Pill)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Branches: $childCount",
                            color = TnTheme.colors.success,
                            fontSize = TnType.Caption
                        )
                    }
                }

                session.tags.take(MAX_TAGS_ON_CARD).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(TnShape.Pill)
                            .border(1.dp, tag.color, TnShape.Pill)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tag.name,
                            color = tag.color,
                            fontSize = TnType.Caption
                        )
                    }
                }
                val hiddenTags = session.tags.size - MAX_TAGS_ON_CARD
                if (hiddenTags > 0) {
                    Box(
                        modifier = Modifier
                            .clip(TnShape.Pill)
                            .border(1.dp, TextSecondary.copy(alpha = 0.5f), TnShape.Pill)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(text = "+$hiddenTags", color = TextSecondary, fontSize = TnType.Caption)
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
                fontFamily = TnTheme.mono,
                fontSize = TnType.Body
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateString,
                color = TextSecondary.copy(alpha = 0.7f), // Make date slightly dimmer
                fontSize = TnType.Caption,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = yearString,
                color = TextSecondary.copy(alpha = 0.5f), // Make year even dimmer for hierarchy
                fontSize = TnType.Caption
            )
        }
    }
}
