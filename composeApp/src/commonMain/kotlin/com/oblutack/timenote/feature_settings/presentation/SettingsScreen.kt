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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() }
) {
    val useMonochromeNodes by viewModel.useMonochromeNodes.collectAsState()
    val enableBackgroundBlur by viewModel.enableBackgroundBlur.collectAsState()
    val enableHaptics by viewModel.enableHaptics.collectAsState()
    val customColors by viewModel.customColors.collectAsState()

    var isColorPickerOpen by remember { mutableStateOf(false) }
    var tempPickedColor by remember { mutableStateOf(DefaultAccentColor) }

    val enableBlur by com.oblutack.timenote.data.repository.SettingsRepository.enableBackgroundBlurFlow.collectAsState(initial = true)

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var isHelpSheetOpen by remember { mutableStateOf(false) }

    val blurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (enableBlur && (isColorPickerOpen || isHelpSheetOpen)) 16.dp else 0.dp, // <-- ADDED isHelpSheetOpen
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "SettingsBlur"
    )

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val scrollState = androidx.compose.foundation.rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .blur(radius = blurRadius)
            .verticalScroll(scrollState)
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

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Dynamic Background Blur", color = TextPrimary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Smoothly blurs the app background when menus open.", color = TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = enableBackgroundBlur,
                onCheckedChange = { viewModel.toggleBackgroundBlur(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DefaultAccentColor)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Haptic Feedback", color = TextPrimary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Vibrate on button presses and timer actions.", color = TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = enableHaptics,
                onCheckedChange = { viewModel.toggleHaptics(it) },
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

            Spacer(modifier = Modifier.height(32.dp))

            Text("SUPPORT & FEEDBACK", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // THE FIX: Wrap it all in a LazyColumn (or Column with weight) so it doesn't get squished!
            Column(modifier = Modifier.fillMaxWidth()) {

                // The Support Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .padding(8.dp)
                ) {
                    // 1. Help & Tutorials
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isHelpSheetOpen = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Help", tint = TextPrimary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Help & Tutorials", color = TextPrimary, fontSize = 16.sp)
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BackgroundDark))

                    // 2. Report a Bug
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            uriHandler.openUri("mailto:timenotesupport@gmail.com?subject=Timenote%20Bug%20Report")
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Bug", tint = TextPrimary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Report a Bug", color = TextPrimary, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Monetization Button
                Button(
                    onClick = { uriHandler.openUri("https://ko-fi.com/oblutack") },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Heart", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buy me a coffee", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp)) // Extra padding at the very bottom
            }

        // Fixed: Replaced weight with a standard height spacer!
        Spacer(modifier = Modifier.height(48.dp))

        // --- DEV TOOL (DELETE BEFORE UPLOAD) ---
        Button(
            onClick = { viewModel.triggerDummyData() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            Text("INJECT DUMMY DATA", color = Color.White, fontWeight = FontWeight.Bold)
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

                // We need a state variable to track the last color we buzzed for
                var lastHapticColorValue by remember { mutableStateOf(0UL) }

                // The Magic Math Canvas
                PremiumColorWheel(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    onColorChanged = { newColor ->
                        tempPickedColor = newColor

                        // THROTTLE LOGIC: Only buzz if the color value changes by a decent chunk
                        // This prevents the motor from exploding while dragging 60fps!
                        if (enableHaptics) {
                            val diff = kotlin.math.abs(newColor.value.toLong() - lastHapticColorValue.toLong())
                            if (diff > 5000000L) { // A math threshold for "significant color change"
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                lastHapticColorValue = newColor.value
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        if (enableHaptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
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

    // --- THE HELP / TUTORIAL BOTTOM SHEET ---
    if (isHelpSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scrollState = androidx.compose.foundation.rememberScrollState() // <-- NEW: Allows scrolling

        ModalBottomSheet(
            onDismissRequest = { isHelpSheetOpen = false },
            sheetState = sheetState,
            containerColor = SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxHeight(0.9f)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("How to use Timenote", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Manual
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("1. The Knowledge Graph", color = DefaultAccentColor, fontWeight = FontWeight.Bold)
                    Text("Timenote isn't just a timer, it's a visual workflow mapper. Tap the branch icon (➕) on any timeline waypoint to spawn a child timer. View how your sessions connect by tapping the Graph icon in the History tab.", color = TextSecondary)

                    Text("2. Interactive Markdown", color = DefaultAccentColor, fontWeight = FontWeight.Bold)
                    Text("Make your notes actionable. Use the toolbar in the text editor to create checklists ([ ]). You can tap these checkboxes directly in the timeline view to check them off without opening the editor!", color = TextSecondary)

                    Text("3. Smart Mentions", color = DefaultAccentColor, fontWeight = FontWeight.Bold)
                    Text("Type '@' in the note editor to instantly search and link to past sessions. Tapping a glowing tag jumps you directly to that session's details.", color = TextSecondary)

                    Text("4. Voice Memos & Ducking", color = DefaultAccentColor, fontWeight = FontWeight.Bold)
                    Text("You can record voice memos directly into your timeline. When you play them back, Timenote will automatically lower the volume of your background music (like Spotify) so you can hear your thoughts clearly.", color = TextSecondary)

                    Text("5. The Flow-State Heatmap", color = DefaultAccentColor, fontWeight = FontWeight.Bold)
                    Text("In the History tab, tap the Heatmap icon to view your focus streaks. Darker squares mean more time spent in deep focus on that specific day.", color = TextSecondary)

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { isHelpSheetOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))
                ) {
                    Text("Got it", color = TextPrimary)
                }
            }
        }
    }
}
