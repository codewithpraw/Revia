package com.contextswitch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = LightBackground,
    primaryContainer = AccentBluePrimaryContainerLight,
    onPrimaryContainer = AccentBlueOnPrimaryContainerLight,
    background = LightBackground,
    onBackground = Color_OnLight,
    surface = LightBackground,
    onSurface = Color_OnLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = LightBackground,
    primaryContainer = AccentBluePrimaryContainerDark,
    onPrimaryContainer = AccentBlueOnPrimaryContainerDark,
    background = DarkBackground,
    onBackground = LightBackground,
    surface = DarkBackground,
    onSurface = LightBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

@Composable
fun ContextSwitchTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = ContextSwitchTypography,
            content = content
        )
    }
}
