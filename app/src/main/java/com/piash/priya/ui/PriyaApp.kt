package com.piash.priya.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.piash.priya.ui.components.VibeAuroraBackground
import com.piash.priya.ui.screens.AboutScreen
import com.piash.priya.ui.screens.ChatScreen
import com.piash.priya.ui.screens.HomeScreen
import com.piash.priya.ui.screens.LogsScreen
import com.piash.priya.ui.screens.SettingsScreen
import com.piash.priya.ui.theme.VibeCyan
import com.piash.priya.ui.theme.VibeMuted
import com.piash.priya.ui.theme.VibePink
import com.piash.priya.ui.theme.VibeViolet

@Composable
fun PriyaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    VibeAuroraBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF0A0613).copy(alpha = 0.85f),
                                    Color(0xFF0A0613)
                                )
                            )
                        )
                ) {
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        NavTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = VibePink,
                                    selectedTextColor = VibePink,
                                    indicatorColor = VibeViolet.copy(alpha = 0.20f),
                                    unselectedIconColor = VibeMuted,
                                    unselectedTextColor = VibeMuted,
                                ),
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = NavTabs.first().route,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                composable("home") { HomeScreen() }
                composable("chat") { ChatScreen() }
                composable("settings") { SettingsScreen() }
                composable("logs") { LogsScreen() }
                composable("about") { AboutScreen() }
            }
        }
    }
}

private data class NavTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val NavTabs = listOf(
    NavTab("home", "Home", Icons.Filled.Home),
    NavTab("chat", "Chat", Icons.Filled.Chat),
    NavTab("settings", "Settings", Icons.Filled.Settings),
    NavTab("logs", "Logs", Icons.Filled.BugReport),
    NavTab("about", "About", Icons.Filled.Info),
)

@Suppress("unused")
private val _refsForR8 = listOf(VibeCyan)
