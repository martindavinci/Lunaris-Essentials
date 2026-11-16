package io.github.martindavinci.lunaris.archives.feature.settings

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme

private const val TEMP_BYTES = 84_000_000L

@Composable
private fun Show(state: SettingsUiState, theme: ThemeMode) {
    LunarisArchivesTheme(theme) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.systemBarsPadding()) {
            SettingsContent(state, language = "English", onAction = {}, onLanguage = {}, onLicences = {}, onSource = {}, onBack = {})
        }
    }
}

@Preview
@Composable
private fun SettingsPreview() = Show(SettingsUiState(AppSettings(theme = ThemeMode.DARK), tempBytes = TEMP_BYTES), ThemeMode.DARK)

@Preview
@Composable
private fun SettingsLightPreview() = Show(SettingsUiState(AppSettings(theme = ThemeMode.LIGHT, rememberRecents = false)), ThemeMode.LIGHT)
