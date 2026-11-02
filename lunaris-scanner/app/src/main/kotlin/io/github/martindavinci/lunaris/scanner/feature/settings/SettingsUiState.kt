package io.github.martindavinci.lunaris.scanner.feature.settings

import io.github.martindavinci.lunaris.scanner.settings.AppSettings
import io.github.martindavinci.lunaris.scanner.settings.ThemeMode

/** [offerClearHistory]: history was just turned off with entries left, so offer to clear them. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val offerClearHistory: Boolean = false,
)

sealed interface SettingsAction {
    data class SetTheme(val theme: ThemeMode) : SettingsAction
    data class SetKeepHistory(val keep: Boolean) : SettingsAction
    data class SetVibrateOnScan(val vibrate: Boolean) : SettingsAction
    data object ClearHistory : SettingsAction
    data object DismissClearHistory : SettingsAction
}
