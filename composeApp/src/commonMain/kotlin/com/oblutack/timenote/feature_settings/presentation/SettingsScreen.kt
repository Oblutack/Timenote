package com.oblutack.timenote.feature_settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() }
) {
    val useMonochromeNodes by viewModel.useMonochromeNodes.collectAsState()
    val customColors by viewModel.customColors.collectAsState()

    var hexInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(top = 24.dp, start = 24.dp, end = 24.dp)
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

        // --- SECTION 1: APPEARANCE ---
        Text("APPEARANCE", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Monochrome Timeline Nodes", color = TextPrimary, fontSize = 16.sp)
            Switch(
                checked = useMonochromeNodes,
                onCheckedChange = { viewModel.toggleMonochromeNodes(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = DefaultAccentColor)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- SECTION 2: CUSTOM PALETTE ---
        Text("CUSTOM COLORS (HEX)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { if (it.length <= 6) hexInput = it },
                placeholder = { Text("e.g. FF9800", color = TextSecondary) },
                prefix = { Text("#", color = TextSecondary) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    viewModel.addCustomColor(hexInput)
                    hexInput = "" // clear after adding
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Add", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Render Custom Colors
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            customColors.forEach { colorLong ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(colorLong.toULong())),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { viewModel.deleteCustomColor(colorLong) }) {
                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}