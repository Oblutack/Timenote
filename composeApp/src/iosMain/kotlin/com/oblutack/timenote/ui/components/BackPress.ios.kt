package com.oblutack.timenote.ui.components

import androidx.compose.runtime.Composable

// iOS has no system Back gesture to intercept here
@Composable
actual fun BackPress(enabled: Boolean, onBack: () -> Unit) = Unit
