package com.oblutack.timenote.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.LocalDate

@Composable
actual fun rememberBackupLauncher(runner: BackupRunner): BackupLauncher {
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runner.exportTo(uri.toString())
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runner.importFrom(uri.toString())
    }
    return remember(exportPicker, importPicker) {
        object : BackupLauncher {
            override fun export() = exportPicker.launch("timenote-backup-${LocalDate.now()}.zip")

            // Some file apps label zip files with a generic type, so accept those too
            override fun import() = importPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
    }
}
