package com.oblutack.timenote.feature_settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.ui.theme.ThemeMode
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType

/** Settings > Appearance: the theme, and (on Android 12+) the wallpaper colours. */
@Composable
fun AppearanceSection(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    dynamicColors: Boolean,
    onDynamicColors: (Boolean) -> Unit,
    dynamicColorsAvailable: Boolean
) {
    Text("APPEARANCE", color = TextSecondary, fontSize = TnType.Caption, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier.fillMaxWidth().clip(TnShape.Card).background(SurfaceDark).padding(16.dp)
    ) {
        Text("Theme", color = TextPrimary, fontSize = TnType.BodyLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == themeMode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(TnShape.Pill)
                        .background(if (selected) DefaultAccentColor.copy(alpha = 0.15f) else TnTheme.colors.surfaceRaised)
                        .then(if (selected) Modifier.border(1.dp, DefaultAccentColor, TnShape.Pill) else Modifier)
                        .clickable { onThemeMode(mode) }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        mode.label,
                        color = if (selected) DefaultAccentColor else TextSecondary,
                        fontSize = TnType.Label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            when (themeMode) {
                ThemeMode.System -> "Follows your phone: dark or light."
                ThemeMode.Dark -> "Dark grey."
                ThemeMode.Amoled -> "Pure black, which saves battery on OLED screens."
                ThemeMode.Light -> "Light."
            },
            color = TextSecondary,
            fontSize = TnType.Caption
        )
    }

    if (dynamicColorsAvailable) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(TnShape.Card).background(SurfaceDark).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Use phone colours", color = TextPrimary, fontSize = TnType.BodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Takes the greys and the accent from your wallpaper.", color = TextSecondary, fontSize = TnType.Caption)
            }
            Switch(
                checked = dynamicColors,
                onCheckedChange = onDynamicColors,
                colors = SwitchDefaults.colors(checkedThumbColor = TnTheme.colors.onAccent, checkedTrackColor = DefaultAccentColor)
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))
}
