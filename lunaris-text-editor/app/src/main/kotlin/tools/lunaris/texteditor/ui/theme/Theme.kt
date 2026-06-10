package tools.lunaris.texteditor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import tools.lunaris.texteditor.settings.AppTheme

private val DarkColors = darkColorScheme(
    primary = EditorAccent,
    background = EditorBackground,
    surface = EditorSurface,
    onSurface = EditorOnSurface,
    onBackground = EditorOnSurface,
)

private val LightColors = lightColorScheme(
    primary = EditorAccent,
)

private val BlackColors = darkColorScheme(
    primary = EditorAccent,
    background = Color.Black,
    surface = Color.Black,
    onSurface = EditorOnSurface,
    onBackground = EditorOnSurface,
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
