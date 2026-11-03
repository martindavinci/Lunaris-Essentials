package io.github.martindavinci.lunaris.pdf.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.settings.AppLocales
import io.github.martindavinci.lunaris.pdf.settings.ThemeMode
import kotlinx.coroutines.launch

private val ListBottomPadding = 24.dp

@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A language change restarts the activity, so reading it once is enough.
    val language = remember { AppLocales.current(context) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val cleared = stringResource(R.string.settings_recents_cleared)
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        if (showLicenses) {
            BackHandler { showLicenses = false }
            LicensesScreen(onBack = { showLicenses = false })
        } else {
            SettingsContent(
                state = state,
                language = language,
                onAction = { action ->
                    viewModel.onAction(action)
                    if (action == SettingsAction.ClearRecents) scope.launch { snackbar.showSnackbar(cleared) }
                },
                onBack = onBack,
                onSetLanguage = { tag -> (context as? Activity)?.let { AppLocales.set(it, tag) } },
                onOpenLicenses = { showLicenses = true },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
    var confirmClear by remember { mutableStateOf(false) }
    var chosenLanguage by remember { mutableStateOf<String?>(null) }

    // Below Android 13 the choice restarts the activity at once. With the dialog still open, its window
    // would keep the focus while it goes away, and the app would stop answering: so the dialog closes first.
    LaunchedEffect(chosenLanguage) {
        chosenLanguage?.let { tag ->
            withFrameNanos { }
            onSetLanguage(tag)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(R.string.settings_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = ListBottomPadding)) {
            SettingsSection(stringResource(R.string.settings_section_appearance)) {
                SettingsRow(
                    Icons.Filled.Palette,
                    stringResource(R.string.settings_theme),
                    themeLabel(state.settings.theme),
                    onClick = { showTheme = true },
                )
                SettingsRow(
                    Icons.Filled.Translate,
                    stringResource(R.string.settings_language),
                    languageLabel(language),
                    onClick = { showLanguage = true },
                )
            }
            SettingsSection(stringResource(R.string.settings_section_reading)) {
                SettingsToggleRow(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.reader_night_pages),
                    subtitle = stringResource(R.string.settings_night_pages_summary),
                    checked = state.settings.nightPages,
                    onCheckedChange = { onAction(SettingsAction.SetNightPages(it)) },
                )
                SettingsToggleRow(
                    icon = Icons.Filled.VerticalAlignTop,
                    title = stringResource(R.string.settings_top_button),
                    subtitle = stringResource(R.string.settings_top_button_summary),
                    checked = state.settings.topButton,
                    onCheckedChange = { onAction(SettingsAction.SetTopButton(it)) },
                )
            }
            SettingsSection(stringResource(R.string.settings_section_recents)) {
                SettingsRow(
                    Icons.Filled.DeleteSweep,
                    stringResource(R.string.settings_clear_recents),
                    stringResource(R.string.settings_clear_recents_summary),
                    onClick = { confirmClear = true },
                )
            }
            SettingsSection(stringResource(R.string.settings_section_about)) {
                AboutCard(onOpenLicenses)
            }
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
        LanguageDialog(current = language, onSelect = { chosenLanguage = it }, onDismiss = { showLanguage = false })
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.recents_clear_title)) },
            text = { Text(stringResource(R.string.recents_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onAction(SettingsAction.ClearRecents)
                }) { Text(stringResource(R.string.recents_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun themeLabel(theme: ThemeMode): String = when (theme) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}
