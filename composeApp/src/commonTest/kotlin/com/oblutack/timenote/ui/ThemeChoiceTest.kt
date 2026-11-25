package com.oblutack.timenote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.oblutack.timenote.ui.theme.AmoledTnColors
import com.oblutack.timenote.ui.theme.DarkTnColors
import com.oblutack.timenote.ui.theme.DynamicColors
import com.oblutack.timenote.ui.theme.LightTnColors
import com.oblutack.timenote.ui.theme.ThemeMode
import com.oblutack.timenote.ui.theme.TnColors
import com.oblutack.timenote.ui.theme.isDarkTheme
import com.oblutack.timenote.ui.theme.resolveTnColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThemeChoiceTest {
    private val wallpaper = DynamicColors(
        background = Color(0xFF101A12), surface = Color(0xFF16221A), surfaceRaised = Color(0xFF1E2E23), surfaceHigh = Color(0xFF28392D),
        onBackground = Color(0xFFE0EEE3), onBackgroundMuted = Color(0xFFA9BBAE), accent = Color(0xFF7AD99A), onAccent = Color(0xFF00391B)
    )

    @Test fun anUnknownOrMissingChoiceIsTheDarkThemeOfEarlierVersions() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.Dark, ThemeMode.fromKey("sepia"))
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromKey(it.key)) }
    }

    @Test fun systemFollowsThePhoneAndTheOthersDoNot() {
        assertTrue(isDarkTheme(ThemeMode.System, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.System, systemDark = false))
        assertTrue(isDarkTheme(ThemeMode.Dark, systemDark = false))
        assertTrue(isDarkTheme(ThemeMode.Amoled, systemDark = false))
        assertFalse(isDarkTheme(ThemeMode.Light, systemDark = true))
    }

    @Test fun eachChoiceGivesItsOwnColours() {
        assertEquals(DarkTnColors, resolveTnColors(ThemeMode.Dark, systemDark = false, dynamic = null))
        assertEquals(AmoledTnColors, resolveTnColors(ThemeMode.Amoled, systemDark = false, dynamic = null))
        assertEquals(LightTnColors, resolveTnColors(ThemeMode.Light, systemDark = true, dynamic = null))
    }

    @Test fun systemOnADarkPhoneIsDarkNotAmoled() {
        assertEquals(DarkTnColors, resolveTnColors(ThemeMode.System, systemDark = true, dynamic = null))
        assertEquals(LightTnColors, resolveTnColors(ThemeMode.System, systemDark = false, dynamic = null))
    }

    @Test fun amoledIsBlackAndOnlyLightIsMarkedLight() {
        assertEquals(Color.Black, AmoledTnColors.background)
        assertTrue(LightTnColors.isLight)
        assertFalse(DarkTnColors.isLight)
        assertFalse(AmoledTnColors.isLight)
        assertTrue(LightTnColors.background.luminance() > 0.5f)
        assertTrue(DarkTnColors.background.luminance() < 0.05f)
    }

    @Test fun phoneColoursReplaceTheGreysAndTheAccent() {
        val c = resolveTnColors(ThemeMode.Dark, systemDark = true, dynamic = wallpaper)
        assertEquals(wallpaper.background, c.background)
        assertEquals(wallpaper.surface, c.surface)
        assertEquals(wallpaper.accent, c.accent)
        assertEquals(wallpaper.onAccent, c.onAccent)
        assertEquals(wallpaper.onBackground, c.onBackground)
    }

    @Test fun phoneColoursNeverChangeWhatAColourMeans() {
        listOf(ThemeMode.Dark, ThemeMode.Amoled, ThemeMode.Light).forEach { mode ->
            val plain = resolveTnColors(mode, systemDark = false, dynamic = null)
            val phone = resolveTnColors(mode, systemDark = false, dynamic = wallpaper)
            assertEquals(plain.success, phone.success)
            assertEquals(plain.danger, phone.danger)
            assertEquals(plain.pause, phone.pause)
            assertEquals(plain.branch, phone.branch)
            assertEquals(plain.untagged, phone.untagged)
        }
    }

    @Test fun amoledStaysBlackWithPhoneColoursAndOnlyTheAccentFollows() {
        val c = resolveTnColors(ThemeMode.Amoled, systemDark = true, dynamic = wallpaper)
        assertEquals(Color.Black, c.background)
        assertEquals(AmoledTnColors.surface, c.surface)
        assertEquals(wallpaper.accent, c.accent)
        assertNotEquals(AmoledTnColors.accent, c.accent)
    }
}

/** Text and signal colours must stay readable on every background they appear on, in every theme. */
class ThemeContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val hi = maxOf(a.luminance(), b.luminance()).toDouble()
        val lo = minOf(a.luminance(), b.luminance()).toDouble()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val themes = mapOf("dark" to DarkTnColors, "amoled" to AmoledTnColors, "light" to LightTnColors)

    private fun check(name: String, c: TnColors, role: String, fg: Color, minimum: Double) {
        listOf("background" to c.background, "surface" to c.surface, "raised" to c.surfaceRaised).forEach { (bgName, bg) ->
            val r = ratio(fg, bg)
            assertTrue(r >= minimum, "$name: $role on $bgName is $r, needs $minimum")
        }
    }

    @Test fun mainTextIsEasyToRead() = themes.forEach { (n, c) -> check(n, c, "text", c.onBackground, 7.0) }

    @Test fun secondaryTextIsReadable() = themes.forEach { (n, c) -> check(n, c, "muted text", c.onBackgroundMuted, 4.5) }

    @Test fun accentAndMeaningColoursAreVisible() = themes.forEach { (n, c) ->
        check(n, c, "accent", c.accent, 3.0)
        check(n, c, "pause", c.pause, 3.0)
        check(n, c, "danger", c.danger, 3.0)
        check(n, c, "success", c.success, 3.0)
        check(n, c, "untagged", c.untagged, 3.0)
        // the branch purple is drawn on the background and cards only; on a raised chip it was never meant to carry text
        listOf(c.background, c.surface).forEach { assertTrue(ratio(c.branch, it) >= 2.5, "$n: branch is too faint") }
    }

    @Test fun layersOfALightThemeAreToldApart() {
        val c = LightTnColors
        assertNotEquals(c.background, c.surface)
        assertNotEquals(c.surface, c.surfaceRaised)
        assertNotEquals(c.surfaceRaised, c.surfaceHigh)
    }
}
