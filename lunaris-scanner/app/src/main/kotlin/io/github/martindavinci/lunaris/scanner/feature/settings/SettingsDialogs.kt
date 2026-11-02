package io.github.martindavinci.lunaris.scanner.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.settings.ThemeMode

@Composable
internal fun themeLabel(theme: ThemeMode): String = when (theme) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
internal fun ThemeDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    val themes = ThemeMode.entries
    SingleChoiceDialog(
        title = stringResource(R.string.settings_theme),
        options = themes.map { themeLabel(it) },
        selectedIndex = themes.indexOf(current),
        onSelect = { i -> onSelect(themes[i]) },
        onDismiss = onDismiss,
    )
}

/** Shown after turning history off: the entries stay unless the user chooses to clear them. */
@Composable
internal fun ClearHistoryDialog(onClear: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        confirmButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.settings_clear_history_confirm)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.settings_clear_history_keep)) } },
        title = { Text(stringResource(R.string.settings_clear_history_title)) },
        text = { Text(stringResource(R.string.settings_clear_history_message)) },
    )
}
