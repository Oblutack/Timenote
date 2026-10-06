package com.oblutack.timenote.backup

import androidx.compose.runtime.Composable

// iOS has no app container yet (see MainViewController), so there is nothing to back up here.
@Composable
actual fun rememberBackupLauncher(runner: BackupRunner): BackupLauncher = object : BackupLauncher {
    override fun export() = Unit
    override fun import() = Unit
}
