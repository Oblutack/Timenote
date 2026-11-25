package com.oblutack.timenote.feature_settings.presentation

import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.Palette
import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.oblutack.timenote.ui.theme.BackgroundDark
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import com.oblutack.timenote.core.OPEN_SOURCE_LIBRARIES
import com.oblutack.timenote.core.PRIVACY_POLICY_URL
import com.oblutack.timenote.core.bugReportLink
import com.oblutack.timenote.core.describeLastBackup
import com.oblutack.timenote.getCurrentTimeMillis
import com.oblutack.timenote.di.settingsViewModel
import com.oblutack.timenote.di.LocalAppContainer
import androidx.compose.material.icons.filled.Share
import com.oblutack.timenote.backup.BackupStatus
import com.oblutack.timenote.backup.rememberBackupLauncher
import com.oblutack.timenote.drive.DriveDebugPanel
import com.oblutack.timenote.drive.isDebugBuild
import com.oblutack.timenote.sync.FeatureFlags
import com.oblutack.timenote.di.syncSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = settingsViewModel()
) {
    val settingsRepository = LocalAppContainer.current.settingsRepository
    val useMonochromeNodes by viewModel.useMonochromeNodes.collectAsState()
    val enableBackgroundBlur by viewModel.enableBackgroundBlur.collectAsState()
    val enableHaptics by viewModel.enableHaptics.collectAsState()
    val customColors by viewModel.customColors.collectAsState()

    var isColorPickerOpen by remember { mutableStateOf(false) }
    var colorToRemove by remember { mutableStateOf<Long?>(null) }
    val pickerStartColor = Palette.Blue
    var tempPickedColor by remember { mutableStateOf(pickerStartColor) }

    val enableBlur by settingsRepository.enableBackgroundBlurFlow.collectAsState(initial = true)

    val backupRunner = LocalAppContainer.current.backupRunner
    val backupStatus by backupRunner.status.collectAsState()
    val backupLauncher = rememberBackupLauncher(backupRunner)

    val uriHandler = LocalUriHandler.current
    val appInfo = LocalAppContainer.current.appInfo
    var isHelpSheetOpen by remember { mutableStateOf(false) }
    var isLicensesOpen by remember { mutableStateOf(false) }

    val blurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (enableBlur && (isColorPickerOpen || isHelpSheetOpen)) 16.dp else 0.dp, // <-- ADDED isHelpSheetOpen
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "SettingsBlur"
    )

    val haptic = LocalHapticFeedback.current

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
            Text("Settings", color = TextPrimary, fontSize = TnType.Headline, fontWeight = FontWeight.Bold)
        }

        // --- PREFERENCES ---
        Text("TIMELINE APPEARANCE", color = TextSecondary, fontSize = TnType.Caption, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TnShape.Card)
                .background(SurfaceDark)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Monochrome Start/End Nodes", color = TextPrimary, fontSize = TnType.BodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Forces start/end nodes to be white/gray.", color = TextSecondary, fontSize = TnType.Caption)
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
                .clip(TnShape.Card)
                .background(SurfaceDark)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Dynamic Background Blur", color = TextPrimary, fontSize = TnType.BodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Smoothly blurs the app background when menus open.", color = TextSecondary, fontSize = TnType.Caption)
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
                .clip(TnShape.Card)
                .background(SurfaceDark)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Haptic Feedback", color = TextPrimary, fontSize = TnType.BodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Vibrate on button presses and timer actions.", color = TextSecondary, fontSize = TnType.Caption)
            }
            Switch(
                checked = enableHaptics,
                onCheckedChange = { viewModel.toggleHaptics(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DefaultAccentColor)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- CUSTOM COLOR PALETTE ---
        Text("CUSTOM COLOR PALETTE", color = TextSecondary, fontSize = TnType.Caption, fontWeight = FontWeight.Bold)
        if (customColors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Press and hold a colour to remove it.", color = TextSecondary.copy(alpha = 0.7f), fontSize = TnType.Caption)
        }
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
                        .combinedClickable(onClick = {}, onLongClick = { colorToRemove = colorLong }),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }

            Spacer(modifier = Modifier.height(32.dp))

            // --- SYNC WITH GOOGLE DRIVE (hidden in release builds until the release that ships sync) ---
            val syncViewModel = syncSettingsViewModel()
            val syncDrive = LocalAppContainer.current.driveSession
            if (syncViewModel != null && syncDrive != null && (isDebugBuild() || FeatureFlags.SYNC_UI_IN_RELEASE)) {
                SyncSettingsSection(syncViewModel, syncDrive)
                Spacer(modifier = Modifier.height(32.dp))
            }

            // --- BACKUP ---
            Text("BACKUP", color = TextSecondary, fontSize = TnType.Caption, fontWeight = FontWeight.Bold)
            val lastBackupAt by settingsRepository.lastBackupAtFlow.collectAsState(initial = null)
            Spacer(modifier = Modifier.height(4.dp))
            Text(describeLastBackup(getCurrentTimeMillis(), lastBackupAt), color = TextSecondary.copy(alpha = 0.7f), fontSize = TnType.Caption)
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TnShape.Card)
                    .background(SurfaceDark)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { backupLauncher.export() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Export backup", color = TextPrimary, fontSize = TnType.BodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Save all timenotes, folders, tags and voice memos to one file. Keep it safe, for example in Google Drive.", color = TextSecondary, fontSize = TnType.Caption)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BackgroundDark))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { backupLauncher.import() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Import backup", color = TextPrimary, fontSize = TnType.BodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add everything from a backup file. Nothing on this device is deleted.", color = TextSecondary, fontSize = TnType.Caption)
                    }
                }
            }

            // --- DEVELOPER ONLY (debug builds) ---
            val driveSession = LocalAppContainer.current.driveSession
            if (driveSession != null && isDebugBuild()) {
                Spacer(modifier = Modifier.height(32.dp))
                DriveDebugPanel(driveSession)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("SUPPORT & FEEDBACK", color = TextSecondary, fontSize = TnType.Caption, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // THE FIX: Wrap it all in a LazyColumn (or Column with weight) so it doesn't get squished!
            Column(modifier = Modifier.fillMaxWidth()) {

                // The Support Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TnShape.Card)
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
                        Text("Help & Tutorials", color = TextPrimary, fontSize = TnType.BodyLarge)
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BackgroundDark))

                    // 2. Report a Bug
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            // the email already says which app and system version it is about
                            uriHandler.openUri(bugReportLink(appInfo))
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Bug", tint = TextPrimary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Report a Bug", color = TextPrimary, fontSize = TnType.BodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- ABOUT ---
                Text("ABOUT", color = TextSecondary, fontSize = TnType.Caption, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TnShape.Card)
                        .background(SurfaceDark)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Version", color = TextPrimary, fontSize = TnType.BodyLarge)
                        Text(appInfo.versionName.ifBlank { "unknown" }, color = TextSecondary, fontSize = TnType.BodyLarge)
                    }
                    if (PRIVACY_POLICY_URL.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BackgroundDark))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(PRIVACY_POLICY_URL) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Privacy policy", color = TextPrimary, fontSize = TnType.BodyLarge)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BackgroundDark))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isLicensesOpen = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Open-source libraries", color = TextPrimary, fontSize = TnType.BodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Monetization Button
                Button(
                    onClick = { uriHandler.openUri("https://ko-fi.com/oblutack") },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = TnShape.Card,
                    colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Heart", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buy me a coffee", fontSize = TnType.BodyLarge, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp)) // Extra padding at the very bottom
            }

        // Fixed: Replaced weight with a standard height spacer!
        // Spacer(modifier = Modifier.height(48.dp))
    }

    colorToRemove?.let { colorLong ->
        AlertDialog(
            onDismissRequest = { colorToRemove = null },
            containerColor = SurfaceDark,
            title = { Text("Remove this colour?", color = TextPrimary) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(colorLong.toULong())))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("It is removed from your palette. Things already using it keep it.", color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCustomColor(colorLong); colorToRemove = null }) {
                    Text("Remove", color = TnTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { colorToRemove = null }) { Text("Cancel", color = TextSecondary) } }
        )
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
                Text("Create Custom Color", color = TextPrimary, fontSize = TnType.Title, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Drag to pick a shade. Saturation fades toward the center.", color = TextSecondary, fontSize = TnType.Caption)

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
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticColorValue = newColor.value
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        if (enableHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.addPickedColor(tempPickedColor)
                        isColorPickerOpen = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = TnShape.Medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tempPickedColor, // The button matches the wheel!
                        contentColor = Color.White
                    )
                ) {
                    Text("Save to Palette", fontSize = TnType.BodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }

    // --- BACKUP PROGRESS / RESULT ---
    when (val status = backupStatus) {
        BackupStatus.Idle -> Unit
        is BackupStatus.Working -> AlertDialog(
            onDismissRequest = {},
            containerColor = SurfaceDark,
            title = { Text(status.label, color = TextPrimary) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = DefaultAccentColor, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Please keep the app open.", color = TextSecondary)
                }
            },
            confirmButton = {}
        )
        is BackupStatus.Finished -> AlertDialog(
            onDismissRequest = { backupRunner.dismiss() },
            containerColor = SurfaceDark,
            title = { Text(status.title, color = TextPrimary) },
            text = { Text(status.message, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { backupRunner.dismiss() }) { Text("OK", color = DefaultAccentColor) }
            }
        )
    }

    if (isLicensesOpen) {
        AlertDialog(
            onDismissRequest = { isLicensesOpen = false },
            containerColor = SurfaceDark,
            title = { Text("Open-source libraries", color = TextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Timenote is built with the work of many people. Thank you.", color = TextSecondary, fontSize = TnType.Body)
                    OPEN_SOURCE_LIBRARIES.forEach { (name, licence) ->
                        Column {
                            Text(name, color = TextPrimary, fontSize = TnType.Body)
                            Text(licence, color = TextSecondary, fontSize = TnType.Caption)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { isLicensesOpen = false }) { Text("Close", color = DefaultAccentColor) } }
        )
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
                Text("How to use Timenote", color = TextPrimary, fontSize = TnType.Headline, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Manual
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("1. The Knowledge Graph", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Timenote isn't just a timer, it's a visual workflow mapper. Tap the branch icon (➕) on any timeline waypoint to spawn a child timer. View how your sessions connect by tapping the Graph icon in the History tab.", color = TextSecondary)

                    Text("2. Interactive Markdown", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Make your notes actionable. Use the toolbar in the text editor to create checklists ([ ]). You can tap these checkboxes directly on the note's detail screen to check them off without opening the editor!", color = TextSecondary)

                    Text("3. Smart Mentions", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Type '@' in the note editor to instantly search and link to past sessions. Tapping a highlighted mention jumps you directly to that session's details.", color = TextSecondary)

                    Text("4. Voice Memos & Ducking", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("You can record voice memos directly into your timeline. When you play them back, Timenote will automatically lower the volume of your background music (like Spotify) so you can hear your thoughts clearly.", color = TextSecondary)

                    Text("5. The Flow-State Heatmap", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("In the History tab, tap the Heatmap icon to view your focus streaks. Darker squares mean more time spent in deep focus on that specific day.", color = TextSecondary)

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { isHelpSheetOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TnTheme.colors.surfaceRaised)
                ) {
                    Text("Got it", color = TextPrimary)
                }
            }
        }
    }
}
