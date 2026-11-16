package io.github.martindavinci.lunaris.archives.feature.settings

import io.github.martindavinci.lunaris.archives.settings.AppSettings
import io.github.martindavinci.lunaris.archives.settings.ThemeMode

/** Settings: the stored values, the size of the temporary files and a one-shot message. */
data class SettingsUiState(val settings: AppSettings = AppSettings(), val tempBytes: Long = 0, val notice: SettingsNotice? = null)
enum class SettingsNotice { Cleared, NoBrowser }

sealed interface SettingsAction {
    data class SetTheme(val mode: ThemeMode) : SettingsAction
    data class SetRememberRecents(val on: Boolean) : SettingsAction
    data object ClearTemporaryFiles : SettingsAction
    data object NoticeShown : SettingsAction
}
