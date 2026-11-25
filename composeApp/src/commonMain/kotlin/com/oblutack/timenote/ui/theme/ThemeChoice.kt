package com.oblutack.timenote.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** What the person picked under Settings > Appearance. */
enum class ThemeMode(val key: String, val label: String) {
    System("system", "System"),
    Dark("dark", "Dark"),
    Amoled("amoled", "AMOLED"),
    Light("light", "Light");

    companion object {
        /** An unknown or missing value is the dark look the app has always had. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: Dark
    }
}

/** The system's own colours (Material You on Android 12+), reduced to the roles this app uses. */
data class DynamicColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val onBackground: Color,
    val onBackgroundMuted: Color,
    val accent: Color,
    val onAccent: Color
)

/** Whether the screen is dark: the choice itself, or the phone's setting for "System". */
fun isDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.System -> systemDark
    ThemeMode.Dark, ThemeMode.Amoled -> true
    ThemeMode.Light -> false
}

/**
 * The colours to paint with. "System" on a dark phone is the normal dark theme, not AMOLED black. System colours replace
 * the greys and the accent; the meaning colours (success, danger, pause, branch, no tag) never change. On AMOLED only the
 * accent follows the wallpaper, because the background has to stay black.
 */
fun resolveTnColors(mode: ThemeMode, systemDark: Boolean, dynamic: DynamicColors?): TnColors {
    val base = when {
        !isDarkTheme(mode, systemDark) -> LightTnColors
        mode == ThemeMode.Amoled -> AmoledTnColors
        else -> DarkTnColors
    }
    if (dynamic == null) return base
    val withAccent = base.copy(accent = dynamic.accent, onAccent = dynamic.onAccent)
    if (mode == ThemeMode.Amoled) return withAccent
    return withAccent.copy(
        background = dynamic.background,
        surface = dynamic.surface,
        surfaceRaised = dynamic.surfaceRaised,
        surfaceHigh = dynamic.surfaceHigh,
        onBackground = dynamic.onBackground,
        onBackgroundMuted = dynamic.onBackgroundMuted
    )
}

/** What only the phone can do for the theme. Android fills it in; other platforms use [NoPlatformTheme]. */
interface PlatformTheme {
    /** True when the wallpaper colours exist on this phone (Android 12+). */
    val supportsDynamicColors: Boolean

    @Composable
    fun dynamicColors(dark: Boolean): DynamicColors?

    /** Colour of the status and navigation bars, and whether their icons should be dark (on a light background). */
    fun applySystemBars(background: Color, lightBackground: Boolean)
}

object NoPlatformTheme : PlatformTheme {
    override val supportsDynamicColors = false

    @Composable
    override fun dynamicColors(dark: Boolean): DynamicColors? = null

    override fun applySystemBars(background: Color, lightBackground: Boolean) = Unit
}

val LocalPlatformTheme = staticCompositionLocalOf<PlatformTheme> { NoPlatformTheme }
