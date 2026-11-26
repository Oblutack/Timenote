package com.oblutack.timenote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType

/**
 * What a list shows when it has nothing in it: what will appear here, and the one step that fills it.
 * Without an [icon] and an action it shrinks to a quiet two-line hint, for places with little room.
 */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TnTheme.colors.onBackgroundMuted.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(title, color = TnTheme.colors.onBackground, fontSize = TnType.BodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(hint, color = TnTheme.colors.onBackgroundMuted, fontSize = TnType.Body, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                shape = TnShape.Pill,
                colors = ButtonDefaults.buttonColors(containerColor = TnTheme.colors.accent, contentColor = TnTheme.colors.onAccent)
            ) {
                Text(actionLabel)
            }
        }
    }
}
