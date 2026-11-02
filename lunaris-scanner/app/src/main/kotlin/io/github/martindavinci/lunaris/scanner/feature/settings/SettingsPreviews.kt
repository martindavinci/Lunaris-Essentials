package io.github.martindavinci.lunaris.scanner.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.martindavinci.lunaris.scanner.settings.AppLanguage
import io.github.martindavinci.lunaris.scanner.settings.ThemeMode
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    LunarisScannerTheme {
        SettingsContent(
            state = SettingsUiState(),
            language = AppLanguage.SYSTEM,
            onAction = {},
            onSetLanguage = {},
            onOpenLicenses = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LicensesScreenPreview() {
    LunarisScannerTheme {
        LicensesScreen(onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ThemeDialogPreview() {
    LunarisScannerTheme {
        ThemeDialog(current = ThemeMode.SYSTEM, onSelect = {}, onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ClearHistoryDialogPreview() {
    LunarisScannerTheme {
        ClearHistoryDialog(onClear = {}, onKeep = {})
    }
}
