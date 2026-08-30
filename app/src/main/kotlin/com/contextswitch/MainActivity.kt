package com.contextswitch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.contextswitch.data.preferences.UserPreferences
import com.contextswitch.ui.screen.HistoryScreen
import com.contextswitch.ui.screen.MainScreen
import com.contextswitch.ui.screen.OnboardingScreen
import com.contextswitch.ui.screen.SettingsScreen
import com.contextswitch.ui.theme.ContextSwitchTheme
import com.contextswitch.ui.theme.ThemeMode
import com.contextswitch.util.Constants

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Constants.Routes.MAIN, "Home", Icons.Filled.Home),
    NavItem(Constants.Routes.HISTORY, "History", Icons.AutoMirrored.Filled.List),
    NavItem(Constants.Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val preferences = remember { UserPreferences(context) }
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            ContextSwitchTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ContextSwitchApp()
                }
            }
        }
    }
}

@Composable
private fun ContextSwitchApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Constants.Routes.ONBOARDING) {
                BottomBar(navController, currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Constants.Routes.ONBOARDING,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Constants.Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Constants.Routes.MAIN) {
                        popUpTo(Constants.Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(Constants.Routes.MAIN) { MainScreen() }
            composable(Constants.Routes.HISTORY) { HistoryScreen() }
            composable(Constants.Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Constants.Routes.MAIN)
                            launchSingleTop = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) }
            )
        }
    }
}
