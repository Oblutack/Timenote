package com.oblutack.timenote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController

// iOS is not supported yet: the app needs a platform AppContainer (database, DataStore, timer
// service, audio) that only exists on Android for now.
fun MainViewController() = ComposeUIViewController {
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        Text("Timenote for iOS is coming soon", color = TextPrimary)
    }
}
