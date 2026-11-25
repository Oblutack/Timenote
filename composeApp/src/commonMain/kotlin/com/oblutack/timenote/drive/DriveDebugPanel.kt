package com.oblutack.timenote.drive

import com.oblutack.timenote.ui.theme.TnTheme
import com.oblutack.timenote.ui.theme.TnShape
import com.oblutack.timenote.ui.theme.TnType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oblutack.timenote.ui.theme.DefaultAccentColor
import com.oblutack.timenote.ui.theme.SurfaceDark
import com.oblutack.timenote.ui.theme.TextPrimary
import com.oblutack.timenote.ui.theme.TextSecondary
import com.oblutack.timenote.getCurrentTimeMillis
import kotlinx.coroutines.launch
import com.oblutack.timenote.di.LocalAppContainer
import com.oblutack.timenote.sync.describeSyncResult

/**
 * Developer-only panel (shown in debug builds only) to try Google Drive for real: connect, then run the round trip
 * from [runDriveSelfTest]. Not part of the product.
 */
@Composable
fun DriveDebugPanel(session: DriveSession) {
    val status by session.status.collectAsState()
    val connect = rememberDriveConnect(session)
    val scope = rememberCoroutineScope()
    val log = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }

    var account by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session) { session.check() }
    LaunchedEffect(status) { account = if (status == DriveStatus.Connected) session.accountId() else null }

    Column(
        modifier = Modifier.fillMaxWidth().clip(TnShape.Card).background(SurfaceDark).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Google Drive test (debug build only)", color = TextPrimary, fontSize = TnType.BodyLarge)
        Text(
            when (val s = status) {
                DriveStatus.Unknown -> "Checking..."
                DriveStatus.SignedOut -> "Not connected"
                DriveStatus.Connected -> "Connected" + (account?.let { " (account id $it)" } ?: " (account unknown)")
                is DriveStatus.Problem -> "Problem: ${s.message}"
            },
            color = TextSecondary, fontSize = TnType.Label
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { session.check() } }) { Text("Check") }
            Button(
                onClick = connect,
                enabled = status != DriveStatus.Connected,
                colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = TnTheme.colors.onAccent)
            ) { Text("Connect Drive") }
        }
        Button(
            onClick = {
                running = true
                log.clear()
                scope.launch {
                    val ok = runDriveSelfTest(session.store) { log += it }
                    log += if (ok) "ALL STEPS PASSED" else "SELF-TEST FAILED"
                    running = false
                }
            },
            enabled = status == DriveStatus.Connected && !running,
            colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = TnTheme.colors.onAccent)
        ) { Text(if (running) "Running..." else "Run round-trip test") }

        val engine = LocalAppContainer.current.syncEngine
        if (engine != null) {
            Button(
                onClick = {
                    running = true
                    log.clear()
                    scope.launch {
                        log += describeSyncResult(engine.sync())
                        running = false
                    }
                },
                enabled = status == DriveStatus.Connected && !running,
                colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = TnTheme.colors.onAccent)
            ) { Text("Sync now (notes and voice memos)") }
        }

        Text("Cross-app check", color = TextSecondary, fontSize = TnType.Label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = status == DriveStatus.Connected && !running,
                onClick = {
                    scope.launch {
                        log.clear()
                        try {
                            val file = leaveWebCheckFile(session.store, getCurrentTimeMillis())
                            log += "left ${file.name} in the app folder (id ${file.id})"
                        } catch (e: RemoteException) { log += "FAIL: ${e.message}" }
                    }
                }
            ) { Text("Leave file") }
            OutlinedButton(
                enabled = status == DriveStatus.Connected && !running,
                onClick = {
                    scope.launch {
                        log.clear()
                        try {
                            val lines = describeAppFolder(session.store)
                            if (lines.isEmpty()) log.add("app folder is empty") else log.addAll(lines)
                        } catch (e: RemoteException) { log += "FAIL: ${e.message}" }
                    }
                }
            ) { Text("List folder") }
            OutlinedButton(
                enabled = status == DriveStatus.Connected && !running,
                onClick = {
                    scope.launch {
                        log.clear()
                        try { log += "deleted ${deleteDebugFiles(session.store)} debug file(s)" }
                        catch (e: RemoteException) { log += "FAIL: ${e.message}" }
                    }
                }
            ) { Text("Clean up") }
        }
        OutlinedButton(
            enabled = status == DriveStatus.Connected && !running,
            onClick = {
                scope.launch {
                    log.clear()
                    try { log += "WIPED ${deleteEverythingInAppFolder(session.store)} file(s) from the cloud folder" }
                    catch (e: RemoteException) { log += "FAIL: ${e.message}" }
                }
            }
        ) { Text("Wipe ALL cloud data (test account only)") }

        if (log.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(log.joinToString("\n"), color = TextPrimary, fontSize = TnType.Caption, fontFamily = FontFamily.Monospace)
        }
    }
}
