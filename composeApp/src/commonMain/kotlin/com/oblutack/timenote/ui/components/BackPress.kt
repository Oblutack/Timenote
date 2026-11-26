package com.oblutack.timenote.ui.components

import androidx.compose.runtime.Composable

/** While [enabled], the system Back gesture runs [onBack] instead of leaving the screen. */
@Composable
expect fun BackPress(enabled: Boolean, onBack: () -> Unit)
