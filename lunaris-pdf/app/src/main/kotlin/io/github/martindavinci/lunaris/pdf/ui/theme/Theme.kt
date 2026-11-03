package io.github.martindavinci.lunaris.pdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.martindavinci.lunaris.pdf.settings.ThemeMode

private const val TILE_ALPHA = 0.14f
private const val CURRENT_ROW_ALPHA = 0.16f
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
    errorContainer = LunarisLightWarning,
    onErrorContainer = LunarisLightOnWarning,
    inverseSurface = LunarisSurfaceVariant,
    inverseOnSurface = LunarisOnSurface,
    inversePrimary = LunarisAccentSoft,
)

/** The soft square behind an icon. */
val ColorScheme.iconTile: Color get() = primary.copy(alpha = TILE_ALPHA)

/** Behind the row of what is being read. */
val ColorScheme.currentRow: Color get() = primary.copy(alpha = CURRENT_ROW_ALPHA)

/** Text on [currentRow]. */
val ColorScheme.currentText: Color
    get() = if (background.luminance() < DARK_LUMINANCE) LunarisAccentSoft else primary

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LunarisPdfTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.isDark()) DarkColors else LightColors,
        typography = LunarisTypography,
        content = content,
    )
}
