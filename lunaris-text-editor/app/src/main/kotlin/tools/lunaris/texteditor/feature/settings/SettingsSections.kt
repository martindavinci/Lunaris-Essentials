package tools.lunaris.texteditor.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tools.lunaris.texteditor.BuildConfig
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.settings.EditorSettings

@Composable
internal fun GeneralSection(
    s: EditorSettings,
    fileFilterOn: Boolean,
    onUpdate: ((EditorSettings) -> EditorSettings) -> Unit,
    onSetFileFilter: (Boolean) -> Unit,
    onShowToolbar: () -> Unit,
    onEncodingClick: () -> Unit,
    onLineBreakClick: () -> Unit,
    onIndentClick: () -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_general))
    SettingsChoiceRow(stringResource(R.string.set_default_encoding), s.defaultEncoding, onEncodingClick)
    SettingsChoiceRow(stringResource(R.string.set_line_break), s.defaultLineEnding, onLineBreakClick)
    SettingsChoiceRow(stringResource(R.string.set_indent), indentLabel(s.indentUnit), onIndentClick)
    SettingsToggleRow(stringResource(R.string.set_word_wrap), null, s.wordWrap) { onUpdate { it.copy(wordWrap = !it.wordWrap) } }
    SettingsToggleRow(stringResource(R.string.set_folder_drawer), null, s.folderDrawerEnabled) { onUpdate { it.copy(folderDrawerEnabled = !it.folderDrawerEnabled) } }
    SettingsToggleRow(stringResource(R.string.set_auto_indent), null, s.autoIndent) { onUpdate { it.copy(autoIndent = !it.autoIndent) } }
    SettingsToggleRow(stringResource(R.string.set_undo_back), null, s.undoWithBack) { onUpdate { it.copy(undoWithBack = !it.undoWithBack) } }
    SettingsToggleRow(stringResource(R.string.set_file_filter), null, fileFilterOn, onSetFileFilter)
    SettingsToggleRow(stringResource(R.string.set_session_restore), null, s.sessionRestore) { onUpdate { it.copy(sessionRestore = !it.sessionRestore) } }
    SettingsNavRow(stringResource(R.string.set_toolbar), onShowToolbar)
}

@Composable
internal fun InputSection(
    s: EditorSettings,
    onUpdate: ((EditorSettings) -> EditorSettings) -> Unit,
    onSuggestionsClick: () -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_input))
    SettingsChoiceRow(stringResource(R.string.set_suggestions), suggestionLabel(s.suggestions), onSuggestionsClick)
    SettingsToggleRow(stringResource(R.string.set_auto_caps), null, s.autoCaps) { onUpdate { it.copy(autoCaps = !it.autoCaps) } }
    SettingsToggleRow(stringResource(R.string.set_accessory), null, s.accessoryBarDefault) { onUpdate { it.copy(accessoryBarDefault = !it.accessoryBarDefault) } }
}

@Composable
internal fun DisplaySection(
    s: EditorSettings,
    onUpdate: ((EditorSettings) -> EditorSettings) -> Unit,
    onFontTypeClick: () -> Unit,
    onFontSizeClick: () -> Unit,
    onLineSpacingClick: () -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_display))
    SettingsToggleRow(stringResource(R.string.set_line_numbers), null, s.lineNumbers) { onUpdate { it.copy(lineNumbers = !it.lineNumbers) } }
    SettingsChoiceRow(
        stringResource(R.string.set_font_type),
        if (s.monospace) stringResource(R.string.font_monospace) else stringResource(R.string.font_normal),
        onFontTypeClick,
    )
    SettingsChoiceRow(stringResource(R.string.set_font_size), "${s.fontSizeSp}", onFontSizeClick)
    SettingsChoiceRow(stringResource(R.string.set_line_spacing), "${s.lineSpacingExtra}", onLineSpacingClick)
}

@Composable
internal fun AutosaveSection(
    s: EditorSettings,
    onUpdate: ((EditorSettings) -> EditorSettings) -> Unit,
    onIntervalClick: () -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_autosave))
    SettingsToggleRow(stringResource(R.string.set_autosave), null, s.autosave) { onUpdate { it.copy(autosave = !it.autosave) } }
    SettingsChoiceRow(stringResource(R.string.set_autosave_interval), "${s.autosaveIntervalMinutes}", onIntervalClick)
}

@Composable
internal fun AppearanceSection(
    s: EditorSettings,
    onUpdate: ((EditorSettings) -> EditorSettings) -> Unit,
    onThemeClick: () -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_appearance))
    SettingsChoiceRow(stringResource(R.string.set_theme), themeLabel(s.appTheme), onThemeClick)
    SettingsToggleRow(stringResource(R.string.set_fullscreen), null, s.fullscreen) { onUpdate { it.copy(fullscreen = !it.fullscreen) } }
}

@Composable
internal fun AboutSection(
    onOpenLicenses: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_about))
    SettingsChoiceRow(stringResource(R.string.set_version), BuildConfig.VERSION_NAME) {}
    SettingsChoiceRow(stringResource(R.string.set_developer), stringResource(R.string.set_developer_value)) {}
    SettingsChoiceRow(stringResource(R.string.set_privacy), stringResource(R.string.set_privacy_value)) {}
    SettingsNavRow(stringResource(R.string.set_licenses), onOpenLicenses)
    SettingsNavRow(stringResource(R.string.set_backup), onBackup)
    SettingsNavRow(stringResource(R.string.set_restore), onRestore)
}
