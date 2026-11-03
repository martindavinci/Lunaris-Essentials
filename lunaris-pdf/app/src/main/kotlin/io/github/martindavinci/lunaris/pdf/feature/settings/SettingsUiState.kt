package io.github.martindavinci.lunaris.pdf.feature.settings

import io.github.martindavinci.lunaris.pdf.settings.AppSettings
import io.github.martindavinci.lunaris.pdf.settings.ThemeMode

data class SettingsUiState(val settings: AppSettings = AppSettings())

sealed interface SettingsAction {
    data class SetTheme(val theme: ThemeMode) : SettingsAction
    data class SetNightPages(val on: Boolean) : SettingsAction
    data class SetTopButton(val on: Boolean) : SettingsAction
}
