package com.oblutack.timenote

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.feature_history.presentation.HistoryScreen
import com.oblutack.timenote.feature_timer.presentation.TimerScreen

// ==========================================
// 1. THEME DEFINITION
// ==========================================
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFAAAAAA)
val DefaultAccentColor = Color(0xFF4FA8F9) // Dynamic accent color later

private val TimenoteColorScheme = darkColorScheme(
    background = BackgroundDark,
    surface = SurfaceDark,
    primary = DefaultAccentColor,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun TimenoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TimenoteColorScheme,
        content = content
    )
}

// ==========================================
// 2. NAVIGATION ENUMS
// ==========================================
enum class Screen {
    Timer, History, Details
}



// ==========================================
// 3. MAIN APP ENTRY POINT
// ==========================================
@Composable
fun App() {
    TimenoteTheme {
        var currentScreen by remember { mutableStateOf(Screen.Timer) }
        var selectedTimenoteId by remember { mutableStateOf<String?>(null) }

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = TextSecondary
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Timer") },
                        label = { Text("Timer") },
                        selected = currentScreen == Screen.Timer,
                        onClick = { currentScreen = Screen.Timer },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DefaultAccentColor,
                            selectedTextColor = DefaultAccentColor,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "History") },
                        label = { Text("History") },
                        selected = currentScreen == Screen.History,
                        onClick = { currentScreen = Screen.History },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DefaultAccentColor,
                            selectedTextColor = DefaultAccentColor,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        ) { innerPadding ->
            Crossfade(
                targetState = currentScreen,
                modifier = Modifier.padding(innerPadding).fillMaxSize().background(BackgroundDark)
            ) { screen ->
                when (screen) {
                    Screen.Timer -> TimerScreen()
                    Screen.History -> HistoryScreen(
                        onTimenoteClick = { id ->
                            selectedTimenoteId = id
                            currentScreen = Screen.Details
                        }
                    )
                    Screen.Details -> {
                        selectedTimenoteId?.let { id ->
                            // Make sure you import TimenoteDetailScreen at the top of App.kt!
                            com.oblutack.timenote.feature_history.presentation.TimenoteDetailScreen(
                                timenoteId = id,
                                onBackClick = { currentScreen = Screen.History } // Goes back to History
                            )
                        }
                    }
                }
            }
        }
    }
}