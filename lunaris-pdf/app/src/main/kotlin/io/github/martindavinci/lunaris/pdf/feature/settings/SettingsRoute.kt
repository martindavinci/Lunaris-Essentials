package io.github.martindavinci.lunaris.pdf.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
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
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.settings.AppLocales
import io.github.martindavinci.lunaris.pdf.settings.ThemeMode

@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A language change restarts the activity, so reading it once is enough.
    val language = remember { AppLocales.current(context) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        if (showLicenses) {
            BackHandler { showLicenses = false }
            LicensesScreen(onBack = { showLicenses = false })
        } else {
            SettingsContent(
                state = state,
                language = language,
                onAction = viewModel::onAction,
                onBack = onBack,
                onSetLanguage = { tag -> (context as? Activity)?.let { AppLocales.set(it, tag) } },
                onOpenLicenses = { showLicenses = true },
            )
        }
    }
}

/** Stateless Settings UI. Holds dialog visibility locally, so a preview needs no ViewModel. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    language: String,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onOpenLicenses: () -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(R.string.settings_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsChoiceRow(stringResource(R.string.settings_theme), themeLabel(state.settings.theme)) { showTheme = true }
            SettingsChoiceRow(stringResource(R.string.settings_language), languageLabel(language)) { showLanguage = true }

            SettingsSectionHeader(stringResource(R.string.settings_section_reading))
            SettingsToggleRow(
                title = stringResource(R.string.reader_night_pages),
                subtitle = stringResource(R.string.settings_night_pages_summary),
                checked = state.settings.nightPages,
                onCheckedChange = { onAction(SettingsAction.SetNightPages(it)) },
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_top_button),
                subtitle = stringResource(R.string.settings_top_button_summary),
                checked = state.settings.topButton,
                onCheckedChange = { onAction(SettingsAction.SetTopButton(it)) },
            )

            SettingsSectionHeader(stringResource(R.string.settings_section_about))
            AboutSummary()
            SettingsNavRow(stringResource(R.string.settings_licenses), onOpenLicenses)
        }
    }

    if (showTheme) {
        val themes = ThemeMode.entries
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = themes.map { themeLabel(it) },
            selectedIndex = themes.indexOf(state.settings.theme),
            onSelect = { i -> onAction(SettingsAction.SetTheme(themes[i])) },
            onDismiss = { showTheme = false },
        )
    }
    if (showLanguage) {
        LanguageDialog(current = language, onSelect = onSetLanguage, onDismiss = { showLanguage = false })
    }
}

@Composable
private fun themeLabel(theme: ThemeMode): String = when (theme) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}
