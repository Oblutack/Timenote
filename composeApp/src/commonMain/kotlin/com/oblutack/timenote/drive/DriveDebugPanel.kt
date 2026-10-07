package com.oblutack.timenote.drive

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
import com.oblutack.timenote.DefaultAccentColor
import com.oblutack.timenote.SurfaceDark
import com.oblutack.timenote.TextPrimary
import com.oblutack.timenote.TextSecondary
import com.oblutack.timenote.getCurrentTimeMillis
import kotlinx.coroutines.launch

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

    LaunchedEffect(session) { session.check() }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceDark).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Google Drive test (debug build only)", color = TextPrimary, fontSize = 16.sp)
        Text(
            when (val s = status) {
                DriveStatus.Unknown -> "Checking..."
                DriveStatus.SignedOut -> "Not connected"
                DriveStatus.Connected -> "Connected"
                is DriveStatus.Problem -> "Problem: ${s.message}"
            },
            color = TextSecondary, fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { session.check() } }) { Text("Check") }
            Button(
                onClick = connect,
                enabled = status != DriveStatus.Connected,
                colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
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
            colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor, contentColor = Color.White)
        ) { Text(if (running) "Running..." else "Run round-trip test") }

        Text("Cross-app check", color = TextSecondary, fontSize = 13.sp)
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

        if (log.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(log.joinToString("\n"), color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
