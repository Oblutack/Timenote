package com.oblutack.timenote.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun BackPress(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled = enabled, onBack = onBack)
