package com.oblutack.timenote.ui.theme

import timenote.composeapp.generated.resources.jetbrainsmono_regular
import timenote.composeapp.generated.resources.jetbrainsmono_light
import timenote.composeapp.generated.resources.jetbrainsmono_bold
import timenote.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a colour is for, not what it looks like. Every theme (dark today; AMOLED, light and system colours later) fills in
 * the same roles, so a screen never needs to know which theme is active.
 */
@Immutable
class TnColors(
    /** The screen behind everything. */
    val background: Color,
    /** Cards, sheets, bars. */
    val surface: Color,
    /** Chips, inputs, empty heatmap cells: one step above [surface]. */
    val surfaceRaised: Color,
    /** The highest step: pause segments, small dots on a card. */
    val surfaceHigh: Color,
    /** Main text and icons. */
    val onBackground: Color,
    /** Secondary text and icons. */
    val onBackgroundMuted: Color,
    /** The one colour for actions and links. */
    val accent: Color,
    /** Something started or finished well. */
    val success: Color,
    /** Delete, discard, end, errors. */
    val danger: Color,
    /** A branch: a session started from another one. */
    val branch: Color,
    /** Time off the task: pauses, the break clock, the pause part of a bar. */
    val pause: Color,
    /** A session without a tag. Neutral on purpose: the accent colour only ever means "you can press this". */
    val untagged: Color
)

val DarkTnColors = TnColors(
    background = Palette.Ink0,
    surface = Palette.Ink1,
    surfaceRaised = Palette.Ink2,
    surfaceHigh = Palette.Ink3,
    onBackground = Palette.White,
    onBackgroundMuted = Palette.Grey,
    accent = Palette.Blue,
    success = Palette.Green,
    danger = Palette.Red,
    branch = Palette.Purple,
    pause = Palette.Sand,
    untagged = Palette.Grey
)

val LocalTnColors = staticCompositionLocalOf { DarkTnColors }

/** The face for every time value: all digits are equally wide, so a running clock never shakes. */
val LocalTnMono = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

object TnTheme {
    val colors: TnColors
        @Composable @ReadOnlyComposable get() = LocalTnColors.current

    /** Time values (clocks, durations, timestamps). Titles and other text keep the system font. */
    val mono: FontFamily
        @Composable @ReadOnlyComposable get() = LocalTnMono.current
}

// The names screens have always used. They now follow the active theme, so no screen had to change when themes arrived.
val BackgroundDark: Color @Composable @ReadOnlyComposable get() = TnTheme.colors.background
val SurfaceDark: Color @Composable @ReadOnlyComposable get() = TnTheme.colors.surface
val TextPrimary: Color @Composable @ReadOnlyComposable get() = TnTheme.colors.onBackground
val TextSecondary: Color @Composable @ReadOnlyComposable get() = TnTheme.colors.onBackgroundMuted
val DefaultAccentColor: Color @Composable @ReadOnlyComposable get() = TnTheme.colors.accent

/** The font sizes in use. Titles and body text pick from here instead of writing a number. */
object TnType {
    val Micro: TextUnit = 10.sp
    val Caption: TextUnit = 12.sp
    val Label: TextUnit = 13.sp
    val Body: TextUnit = 14.sp
    val BodyLarge: TextUnit = 16.sp
    val Subtitle: TextUnit = 18.sp
    val Title: TextUnit = 20.sp
    val Headline: TextUnit = 24.sp
}

/** The corner shapes in use. The app is flat: nothing casts a shadow, so there is no elevation scale. */
object TnShape {
    /** Fully rounded ends: chips, buttons, segmented controls. */
    val Pill = RoundedCornerShape(50)
    /** Cards and list rows. */
    val Card = RoundedCornerShape(16.dp)
    /** Small inner elements: note blocks, thumbnails. */
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    /** Sheets and dialogs. */
    val Large = RoundedCornerShape(24.dp)
}

/** The spacing steps for new layouts (the existing screens keep their values until they are redone). */
object TnSpace {
    val Xs = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
}

private fun TnColors.toMaterial() = darkColorScheme(
    background = background,
    surface = surface,
    primary = accent,
    onPrimary = Color.White,
    onBackground = onBackground,
    onSurface = onBackground
)

@Composable
private fun rememberMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrainsmono_light, FontWeight.Light),
    Font(Res.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(Res.font.jetbrainsmono_bold, FontWeight.Bold)
)

@Composable
fun TimenoteTheme(colors: TnColors = DarkTnColors, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalTnColors provides colors, LocalTnMono provides rememberMonoFamily()) {
        MaterialTheme(colorScheme = colors.toMaterial(), content = content)
    }
}
