package io.github.martindavinci.lunaris.pdf.feature.settings

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.pdf.settings.AppLanguage
import io.github.martindavinci.lunaris.pdf.ui.theme.LunarisPdfTheme

@Preview
@Composable
private fun SettingsContentPreview() {
    LunarisPdfTheme {
        Surface {
            SettingsContent(
                state = SettingsUiState(),
                language = AppLanguage.SYSTEM,
                onAction = {},
                onBack = {},
                onSetLanguage = {},
                onOpenLicenses = {},
            )
        }
    }
}
