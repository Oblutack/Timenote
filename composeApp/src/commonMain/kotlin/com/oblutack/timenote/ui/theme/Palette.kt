package com.oblutack.timenote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw colours of the app. This file and [TnColors] are the only places where a colour is written as a number.
 *
 * Screens should ask for a role (`TnTheme.colors.danger`), which can differ per theme. [Palette] itself is for colours that
 * are stored as data (the colour of a tag, a folder, a note) and for code that is not a composable (view models, models).
 */
object Palette {
    val Blue = Color(0xFF4FA8F9)
    val Green = Color(0xFF4CAF50)
    val Orange = Color(0xFFFF9800)
    val Purple = Color(0xFF9C27B0)
    /** The colour of an interruption in the timeline: warm, but not the red of delete and errors. */
    val Coral = Color(0xFFFF7043)
    val Red = Color(0xFFE53935)
    val Cyan = Color(0xFF00BCD4)
    /** A calm amber for time off the task. Kept apart from the saturated tag orange. */
    val Sand = Color(0xFFE3B04B)

    val Ink0 = Color(0xFF121212)
    val Ink1 = Color(0xFF1E1E1E)
    val Ink2 = Color(0xFF2C2C2C)
    val Ink3 = Color(0xFF333333)
    val White = Color(0xFFFFFFFF)
    val Grey = Color(0xFFAAAAAA)
}

/** The colours offered when making a tag, a folder or a note colour, in the order they are shown. */
val TagColorChoices = listOf(Palette.Blue, Palette.Green, Palette.Orange, Palette.Purple, Palette.Red, Palette.Cyan)
