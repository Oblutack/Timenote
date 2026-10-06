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
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
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
