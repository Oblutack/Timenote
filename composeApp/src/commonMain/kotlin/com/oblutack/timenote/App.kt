package com.oblutack.timenote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oblutack.timenote.feature_history.presentation.HistoryScreen
import com.oblutack.timenote.feature_timer.presentation.TimerScreen
import com.oblutack.timenote.feature_history.presentation.FolderDetailScreen
import com.oblutack.timenote.feature_history.presentation.GraphScreen
import com.oblutack.timenote.feature_history.presentation.HistoryViewModel
import com.oblutack.timenote.feature_history.presentation.TimenoteDetailScreen
import com.oblutack.timenote.feature_history.presentation.TrashScreen
import com.oblutack.timenote.feature_settings.presentation.SettingsScreen
import com.oblutack.timenote.feature_timer.presentation.TimerAction
import com.oblutack.timenote.feature_timer.presentation.TimerViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.oblutack.timenote.di.AppContainer
import com.oblutack.timenote.di.LocalAppContainer
import com.oblutack.timenote.di.historyViewModel
import com.oblutack.timenote.di.timerViewModel

// ==========================================
// 1. THEME DEFINITION
// ==========================================
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFAAAAAA)
val DefaultAccentColor = Color(0xFF4FA8F9)

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
// 2. MAIN APP ENTRY POINT (Using NavHost)
// ==========================================
@Composable
fun App(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        AppContent()
    }
}

@Composable
private fun AppContent() {
    TimenoteTheme {
        // --- NEW: Official Navigation Controller ---
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // We only need to remember the History Tab state now!
        var historyTab by remember { mutableStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = TextSecondary
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Timer") },
                        label = { Text("Timer") },
                        selected = currentRoute == "timer",
                        onClick = {
                            navController.navigate("timer") {
                                // Prevents building up a massive backstack if you click the tab 10 times
                                navController.graph.startDestinationRoute?.let { startRoute -> popUpTo(startRoute) { saveState = true } }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
                        selected = currentRoute?.startsWith("history") == true, // Highlight if on history OR its sub-screens
                        onClick = {
                            navController.navigate("history") {
                                navController.graph.startDestinationRoute?.let { startRoute -> popUpTo(startRoute) { saveState = true } }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
            // --- NEW: NavHost handles all the screen transitions and back gestures! ---
            NavHost(
                navController = navController,
                startDestination = "timer",
                modifier = Modifier.padding(innerPadding).fillMaxSize().background(BackgroundDark)
            ) {
                composable(
                    route = "timer?parentId={parentId}&waypointId={waypointId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("parentId") { nullable = true; defaultValue = null },
                        androidx.navigation.navArgument("waypointId") { nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    val parentId = backStackEntry.arguments?.getString("parentId")
                    val waypointId = backStackEntry.arguments?.getString("waypointId")

                    val timerViewModel: TimerViewModel = timerViewModel()

                    LaunchedEffect(parentId, waypointId) {
                        if (parentId != null && waypointId != null) {
                            timerViewModel.onAction(TimerAction.SetParentLinks(parentId, waypointId))
                        }
                    }

                    TimerScreen(viewModel = timerViewModel)
                }
                composable("history") {
                    HistoryScreen(
                        selectedTab = historyTab,
                        onTabSelected = { historyTab = it },
                        onTimenoteClick = { id -> navController.navigate("details/$id") },
                        onFolderClick = { id -> navController.navigate("folder_details/$id") },
                        onTrashClick = { navController.navigate("trash") },
                        onSettingsClick = { navController.navigate("settings") },
                        onGraphClick = { navController.navigate("graph") },
                    )
                }

                composable("graph") {
                    val historyViewModel: HistoryViewModel = historyViewModel()
                    GraphScreen(
                        onBackClick = { navController.popBackStack() },
                        onTimenoteClick = { id -> navController.navigate("details/$id") },
                        viewModel = historyViewModel // <-- NEW
                    )
                }

                composable("details/{id}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")
                    if (id != null) {
                        TimenoteDetailScreen(
                            timenoteId = id,
                            onBackClick = { navController.popBackStack() },
                            // --- NEW: Allow jumping to child timenotes ---
                            onTimenoteClick = { childId -> navController.navigate("details/$childId") },
                            // ---------------------------------------------
                            onBranchClick = { parentId, waypointId ->
                                navController.navigate("timer?parentId=$parentId&waypointId=$waypointId") {
                                    popUpTo("timer") { inclusive = false }
                                }
                            }
                        )
                    }
                }
                composable("folder_details/{id}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")
                    if (id != null) {
                        FolderDetailScreen(
                            folderId = id,
                            onBackClick = { navController.popBackStack() },
                            onTimenoteClick = { noteId -> navController.navigate("details/$noteId") },
                            onStartSessionClick = {
                                navController.navigate("timer") {
                                    popUpTo("timer") { inclusive = false }
                                }
                            }
                        )
                    }
                }
                composable("trash") {
                    // Assuming you have TrashScreen imported or using full package path:
                    TrashScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}