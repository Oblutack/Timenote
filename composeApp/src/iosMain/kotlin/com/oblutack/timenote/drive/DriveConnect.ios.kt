package com.oblutack.timenote.drive

import androidx.compose.runtime.Composable

// iOS has no app container yet (see MainViewController), so there is no Drive session to connect.
@Composable
actual fun rememberDriveConnect(session: DriveSession): () -> Unit = {}

@Composable
actual fun isDebugBuild(): Boolean = false
