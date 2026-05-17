package com.oblutack.timenote.feature_settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oblutack.timenote.BackgroundDark
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() }
) {
    val useMonochromeNodes by viewModel.useMonochromeNodes.collectAsState()
    val customColors by viewModel.customColors.collectAsState()

    var isColorPickerOpen by remember { mutableStateOf(false) }
    var tempPickedColor by remember { mutableStateOf(DefaultAccentColor) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        // --- TOP BAR ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // --- PREFERENCES ---
        Text("TIMELINE APPEARANCE", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Monochrome Start/End Nodes", color = TextPrimary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Forces start/end nodes to be white/gray.", color = TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = useMonochromeNodes,
                onCheckedChange = { viewModel.toggleMonochromeNodes(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DefaultAccentColor)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- CUSTOM COLOR PALETTE ---
        Text("CUSTOM COLOR PALETTE", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The "Add" Button
            item {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(1.dp, TextSecondary.copy(alpha = 0.5f), CircleShape)
                        .clickable { isColorPickerOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Color", tint = TextSecondary)
                }
            }

            // The Saved Colors
            items(customColors) { colorLong ->
                val color = Color(colorLong.toULong())
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { viewModel.deleteCustomColor(colorLong) }, // Tap to delete!
                    contentAlignment = Alignment.Center
                ) {
                    // Show a faint X to indicate it's deletable
                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    // --- THE COLOR WHEEL BOTTOM SHEET ---
    if (isColorPickerOpen) {

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { isColorPickerOpen = false },
            sheetState = sheetState,
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Create Custom Color", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Drag to pick a shade. Saturation fades toward the center.", color = TextSecondary, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(32.dp))

                // The Magic Math Canvas
                PremiumColorWheel(
                    modifier = Modifier.fillMaxWidth(0.85f), // Scales beautifully
                    onColorChanged = { tempPickedColor = it }
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        viewModel.addPickedColor(tempPickedColor)
                        isColorPickerOpen = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tempPickedColor, // The button matches the wheel!
                        contentColor = Color.White
                    )
                ) {
                    Text("Save to Palette", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}