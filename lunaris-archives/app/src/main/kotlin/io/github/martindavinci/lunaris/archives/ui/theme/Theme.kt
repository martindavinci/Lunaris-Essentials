package io.github.martindavinci.lunaris.archives.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.martindavinci.lunaris.archives.settings.ThemeMode

private const val TILE_ALPHA = 0.14f
private const val SELECTED_DARK_ALPHA = 0.6f
private const val SELECTED_LIGHT_ALPHA = 0.16f
private const val DARK_LUMINANCE = 0.5f

private val DarkColors = darkColorScheme(
    primary = LunarisAccent,
    onPrimary = LunarisOnAccent,
    primaryContainer = LunarisDeepBlue,
    onPrimaryContainer = LunarisOnDeepBlue,
    secondary = LunarisAccent,
    onSecondary = LunarisOnAccent,
    secondaryContainer = LunarisDeepBlue,
    onSecondaryContainer = LunarisOnDeepBlue,
    background = LunarisBackground,
    onBackground = LunarisOnSurface,
    surface = LunarisSurface,
    onSurface = LunarisOnSurface,
    surfaceVariant = LunarisSurfaceVariant,
    onSurfaceVariant = LunarisOnSurfaceVariant,
    surfaceContainer = LunarisSurfaceVariant,
    surfaceContainerHigh = LunarisSurfaceHigh,
    surfaceContainerHighest = LunarisSurfaceVariant,
    surfaceTint = Color.Transparent,
    outline = LunarisOutline,
    outlineVariant = LunarisOutline,
    error = LunarisError,
    errorContainer = LunarisWarning,
    onErrorContainer = LunarisOnWarning,
    inverseSurface = LunarisOnSurface,
    inverseOnSurface = LunarisSurface,
    inversePrimary = LunarisLightAccent,
)

private val LightColors = lightColorScheme(
    primary = LunarisLightAccent,
    onPrimary = LunarisLightOnAccent,
    primaryContainer = LunarisDeepBlue,
    onPrimaryContainer = LunarisOnDeepBlue,
    secondary = LunarisLightAccent,
    onSecondary = LunarisLightOnAccent,
    secondaryContainer = LunarisDeepBlue,
    onSecondaryContainer = LunarisOnDeepBlue,
    background = LunarisLightBackground,
    onBackground = LunarisLightOnSurface,
    surface = LunarisLightSurface,
    onSurface = LunarisLightOnSurface,
    surfaceVariant = LunarisLightSurfaceVariant,
    onSurfaceVariant = LunarisLightOnSurfaceVariant,
    surfaceContainer = LunarisLightSurface,
    surfaceContainerHigh = LunarisLightSurface,
    surfaceContainerHighest = LunarisLightSurfaceVariant,
    surfaceTint = Color.Transparent,
    outline = LunarisLightOutline,
    outlineVariant = LunarisLightOutline,
    error = LunarisLightError,
    errorContainer = LunarisLightWarning,
    onErrorContainer = LunarisLightOnWarning,
    inverseSurface = LunarisSurfaceVariant,
    inverseOnSurface = LunarisOnSurface,
    inversePrimary = LunarisAccentSoft,
)

/** The soft square behind an icon. */
val ColorScheme.iconTile: Color get() = primary.copy(alpha = TILE_ALPHA)

private fun ColorScheme.isDarkScheme(): Boolean = background.luminance() < DARK_LUMINANCE

/** Accent text that stays readable on dark and light surfaces. */
val ColorScheme.accentText: Color
    get() = if (isDarkScheme()) LunarisAccentSoft else primary

/** The square behind an entry's icon. */
val ColorScheme.entryTile: Color
    get() = if (isDarkScheme()) surfaceContainerHigh else surfaceVariant

/** Behind a selected row. */
val ColorScheme.selectedRow: Color
    get() = if (isDarkScheme()) primaryContainer.copy(alpha = SELECTED_DARK_ALPHA) else primary.copy(alpha = SELECTED_LIGHT_ALPHA)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LunarisArchivesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.isDark()) DarkColors else LightColors,
        typography = LunarisTypography,
        content = content,
    )
}
