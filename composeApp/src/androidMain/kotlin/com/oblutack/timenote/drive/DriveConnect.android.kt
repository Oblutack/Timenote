package com.oblutack.timenote.drive

import android.content.pm.ApplicationInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDriveConnect(session: DriveSession): () -> Unit {
    val android = session as? AndroidDriveSession ?: return {}
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        android.onConsentResult(result.data)
    }
    return remember(launcher, android) {
        {
            val pending = android.tokens.consentIntent
            if (pending != null) launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }
    }
}

@Composable
actual fun isDebugBuild(): Boolean {
    val context = LocalContext.current
    return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
