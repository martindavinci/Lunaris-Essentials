package tools.lunaris.texteditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun LunarisEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LunarisTypography,
        content = content,
    )
}
