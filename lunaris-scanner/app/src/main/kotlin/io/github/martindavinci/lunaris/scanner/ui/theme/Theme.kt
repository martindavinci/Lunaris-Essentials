package io.github.martindavinci.lunaris.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import io.github.martindavinci.lunaris.scanner.settings.ThemeMode

private val DarkColors = darkColorScheme(
    primary = LunarisAccent,
    onPrimary = LunarisOnAccent,
    secondary = LunarisAccent,
    onSecondary = LunarisOnAccent,
    background = LunarisBackground,
    onBackground = LunarisOnSurface,
    surface = LunarisSurface,
    onSurface = LunarisOnSurface,
    surfaceVariant = LunarisSurfaceVariant,
    onSurfaceVariant = LunarisOnSurfaceVariant,
    outline = LunarisOutline,
)

private val LightColors = lightColorScheme(
    primary = LunarisAccent,
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LunarisScannerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.isDark()) DarkColors else LightColors,
        typography = LunarisTypography,
        content = content,
    )
}
