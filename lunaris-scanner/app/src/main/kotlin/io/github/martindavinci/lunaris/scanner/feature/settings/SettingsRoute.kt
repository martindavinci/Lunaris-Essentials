package io.github.martindavinci.lunaris.scanner.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.settings.AppLocales

@Composable
fun SettingsRoute() {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A language change restarts the activity, so reading it once is enough.
    val language = remember { AppLocales.current(context) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    if (showLicenses) {
        BackHandler { showLicenses = false }
        LicensesScreen(onBack = { showLicenses = false })
    } else {
        SettingsContent(
            state = state,
            language = language,
            onAction = viewModel::onAction,
            onSetLanguage = { tag -> (context as? Activity)?.let { AppLocales.set(it, tag) } },
            onOpenLicenses = { showLicenses = true },
        )
    }
}

/** Stateless Settings UI. Holds dialog visibility locally, so a preview needs no ViewModel. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    language: String,
    onAction: (SettingsAction) -> Unit,
    onSetLanguage: (String) -> Unit,
    onOpenLicenses: () -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(R.string.nav_settings))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsChoiceRow(stringResource(R.string.settings_theme), themeLabel(state.settings.theme)) { showTheme = true }
            SettingsChoiceRow(stringResource(R.string.settings_language), languageLabel(language)) { showLanguage = true }

            SettingsSectionHeader(stringResource(R.string.settings_section_scanning))
            SettingsToggleRow(
                title = stringResource(R.string.settings_keep_history),
                subtitle = stringResource(R.string.settings_keep_history_summary),
                checked = state.settings.keepHistory,
                onCheckedChange = { onAction(SettingsAction.SetKeepHistory(it)) },
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_vibrate_on_scan),
                subtitle = null,
                checked = state.settings.vibrateOnScan,
                onCheckedChange = { onAction(SettingsAction.SetVibrateOnScan(it)) },
            )

            SettingsSectionHeader(stringResource(R.string.settings_section_about))
            AboutSummary()
            SettingsNavRow(stringResource(R.string.settings_licenses), onOpenLicenses)
        }
    }

    if (showTheme) {
        ThemeDialog(
            current = state.settings.theme,
            onSelect = { onAction(SettingsAction.SetTheme(it)) },
            onDismiss = { showTheme = false },
        )
    }
    if (showLanguage) {
        LanguageDialog(current = language, onSelect = onSetLanguage, onDismiss = { showLanguage = false })
    }
    if (state.offerClearHistory) {
        ClearHistoryDialog(
            onClear = { onAction(SettingsAction.ClearHistory) },
            onKeep = { onAction(SettingsAction.DismissClearHistory) },
        )
    }
}
