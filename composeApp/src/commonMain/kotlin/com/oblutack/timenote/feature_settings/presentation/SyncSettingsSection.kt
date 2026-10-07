package com.oblutack.timenote.feature_settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.drive.DriveSession
import com.oblutack.timenote.drive.DriveStatus
import com.oblutack.timenote.drive.rememberDriveConnect
import com.oblutack.timenote.getCurrentTimeMillis
import com.oblutack.timenote.sync.describeLastSync
import com.oblutack.timenote.sync.describeLinkPreview

private val Red = Color(0xFFE53935)

/** "Sync with Google Drive": connect, see what will happen, turn it off, remove the cloud copy. */
@Composable
fun SyncSettingsSection(viewModel: SyncSettingsViewModel, session: DriveSession) {
    val state by viewModel.state.collectAsState()
    val dialog by viewModel.linkDialog.collectAsState()
    val connect = rememberDriveConnect(session)
    var awaitingConsent by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    // Back from Google's consent screen with access granted: show what connecting will do before anything happens
    LaunchedEffect(state.connection, awaitingConsent, state.enabled) {
        if (awaitingConsent && !state.enabled && state.connection == DriveStatus.Connected) {
            awaitingConsent = false
            viewModel.showLinkPreview()
        }
    }

    Text("SYNC WITH GOOGLE DRIVE", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceDark).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.enabled) {
            Text("Keep your timenotes on all your devices", color = TextPrimary, fontSize = 16.sp)
            Text(
                "Your timenotes and voice memos are copied to your own Google Drive, in a hidden folder that only Timenote can " +
                    "open. If you lose your phone, sign in on a new one and everything comes back. Timenote has no server and cannot see your data.",
                color = TextSecondary, fontSize = 12.sp
            )
            val connected = state.connection == DriveStatus.Connected
            if (connected) state.accountLabel?.let { Text("Google account: $it", color = TextSecondary, fontSize = 12.sp) }
            Button(
                onClick = {
                    if (connected) viewModel.showLinkPreview() else { awaitingConsent = true; connect() }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
            ) { Text(if (connected) "Review and start syncing" else "Connect Google Drive") }
        } else {
            Text(
                "Syncing with ${state.accountLabel ?: "Google Drive"}",
                color = TextPrimary, fontSize = 16.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.syncing) {
                    CircularProgressIndicator(color = DefaultAccentColor, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.padding(start = 8.dp))
                }
                Text(
                    if (state.syncing) "Syncing..." else describeLastSync(getCurrentTimeMillis(), state.lastSyncAt),
                    color = TextSecondary, fontSize = 13.sp
                )
            }
            state.problem?.let {
                Text(it.text, color = if (it.needsUser) Red else TextSecondary, fontSize = 12.sp)
            }
            if (state.problem?.needsUser == true && state.connection != DriveStatus.Connected) {
                Button(
                    onClick = { connect() },
                    colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
                ) { Text("Connect again") }
            }
            OutlinedButton(onClick = { viewModel.syncNow() }, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                Text("Sync now")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Upload voice memos on Wi-Fi only", color = TextPrimary, fontSize = 14.sp)
                    Text("Voice memos are large. Turn this off to also upload them on mobile data.", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = state.voiceWifiOnly,
                    onCheckedChange = { viewModel.setVoiceWifiOnly(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DefaultAccentColor)
                )
            }
            TextButton(onClick = { viewModel.turnOff() }) { Text("Turn off sync", color = TextPrimary) }
            Text(
                "Your timenotes stay on this phone, and the copy in Google Drive is not touched.",
                color = TextSecondary, fontSize = 12.sp
            )
            TextButton(onClick = { confirmDelete = true }) { Text("Delete my data from Google Drive...", color = Red) }
        }
        // not enabled: show why connecting did not work
        if (!state.enabled) state.problem?.let { Text(it.text, color = Red, fontSize = 12.sp) }
        if (!state.enabled) (state.connection as? DriveStatus.Problem)?.let { Text(it.message, color = Red, fontSize = 12.sp) }
    }

    // ---- what connecting will do
    when (val d = dialog) {
        LinkDialog.Hidden -> Unit
        LinkDialog.Loading -> AlertDialog(
            onDismissRequest = { viewModel.cancelLink() },
            containerColor = SurfaceDark,
            title = { Text("Looking at your Google Drive...", color = TextPrimary) },
            text = { CircularProgressIndicator(color = DefaultAccentColor) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { viewModel.cancelLink() }) { Text("Cancel", color = TextSecondary) } }
        )
        is LinkDialog.Ready -> AlertDialog(
            onDismissRequest = { viewModel.cancelLink() },
            containerColor = SurfaceDark,
            title = { Text("Start syncing?", color = TextPrimary) },
            text = { Text(describeLinkPreview(d.preview), color = TextSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.confirmLink() }) { Text("Start syncing", color = DefaultAccentColor, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { viewModel.cancelLink() }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    // ---- removing the cloud copy
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SurfaceDark,
            title = { Text("Delete your data from Google Drive?", color = TextPrimary) },
            text = {
                Text(
                    "This removes every timenote, tag, folder and voice memo that Timenote saved in your Google Drive and turns sync off. " +
                        "Everything on this phone stays. Other phones that sync with it will keep their own copy.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteCloudData() }) { Text("Delete", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    // ---- another Google account is signed in
    state.accountChange?.let {
        AlertDialog(
            onDismissRequest = { viewModel.keepOldAccount() },
            containerColor = SurfaceDark,
            title = { Text("Different Google account", color = TextPrimary) },
            text = {
                Text(
                    "This phone syncs with another Google account than the one that is signed in now. Nothing was changed. " +
                        "If you use the signed-in account, the timenotes on this phone are merged into its Google Drive; nothing is deleted.",
                    color = TextSecondary
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.useNewAccount() }) { Text("Use the signed-in account", color = DefaultAccentColor, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { viewModel.keepOldAccount() }) { Text("Not now", color = TextSecondary) } }
        )
    }
}
