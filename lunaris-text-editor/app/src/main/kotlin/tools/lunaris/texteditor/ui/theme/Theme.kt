package tools.lunaris.texteditor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import tools.lunaris.texteditor.settings.AppTheme

private val DarkColors = darkColorScheme(
    primary = EditorAccent,
    onPrimary = EditorOnAccent,
    secondary = EditorAccent,
    onSecondary = EditorOnAccent,
    background = EditorBackground,
    onBackground = EditorOnSurface,
    surface = EditorSurface,
    onSurface = EditorOnSurface,
    surfaceVariant = EditorSurfaceVariant,
    onSurfaceVariant = EditorOnSurfaceVariant,
    outline = EditorOutline,
)

private val LightColors = lightColorScheme(
    primary = EditorAccent,
)

private val BlackColors = darkColorScheme(
    primary = EditorAccent,
    onPrimary = EditorOnAccent,
    secondary = EditorAccent,
    onSecondary = EditorOnAccent,
    background = Color.Black,
    onBackground = EditorOnSurface,
    surface = Color.Black,
    onSurface = EditorOnSurface,
    // Slightly-raised near-black so cards/board/search field stay visible on AMOLED black.
    surfaceVariant = Color(0xFF16181C),
    onSurfaceVariant = EditorOnSurfaceVariant,
    outline = EditorOutline,
)

@Composable
fun LunarisEditorTheme(
    appTheme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit,
) {
    val colors = when (appTheme) {
        AppTheme.LIGHT -> LightColors
        AppTheme.DARK -> DarkColors
        AppTheme.BLACK -> BlackColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = LunarisTypography,
        content = content,
    )
}
