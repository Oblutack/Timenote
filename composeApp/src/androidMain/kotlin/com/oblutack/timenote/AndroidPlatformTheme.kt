package com.oblutack.timenote

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.oblutack.timenote.ui.theme.DynamicColors
import com.oblutack.timenote.ui.theme.PlatformTheme

/** The wallpaper colours (Android 12+) and the status and navigation bars of this activity's window. */
class AndroidPlatformTheme(private val activity: Activity) : PlatformTheme {

    override val supportsDynamicColors: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @Composable
    override fun dynamicColors(dark: Boolean): DynamicColors? {
        if (!supportsDynamicColors) return null
        val context = LocalContext.current
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return DynamicColors(
            background = scheme.surface,
            surface = scheme.surfaceContainer,
            surfaceRaised = scheme.surfaceContainerHigh,
            surfaceHigh = scheme.surfaceContainerHighest,
            onBackground = scheme.onSurface,
            onBackgroundMuted = scheme.onSurfaceVariant,
            accent = scheme.primary,
            onAccent = scheme.onPrimary
        )
    }

    @Suppress("DEPRECATION")
    override fun applySystemBars(background: Color, lightBackground: Boolean) {
        val window = activity.window
        val argb = background.toArgb()
        // Behind the app (shown while it rotates or resizes) and behind the bars
        window.setBackgroundDrawable(ColorDrawable(argb))
        window.statusBarColor = argb
        window.navigationBarColor = argb
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}
