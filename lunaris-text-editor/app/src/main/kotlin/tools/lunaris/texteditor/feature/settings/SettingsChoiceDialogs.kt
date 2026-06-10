package tools.lunaris.texteditor.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.settings.AppTheme
import tools.lunaris.texteditor.settings.EditorSettings
import tools.lunaris.texteditor.settings.Suggestions

@Composable
internal fun SettingsChoiceDialogs(
    openChoice: SettingChoice?,
    s: EditorSettings,
    onUpdate: ((EditorSettings) -> EditorSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    when (openChoice) {
        SettingChoice.ENCODING -> {
            val options = listOf("Auto", "UTF-8", "UTF-16", "UTF-16LE", "UTF-16BE", "ISO-8859-1", "windows-1252", "GBK", "Shift_JIS", "EUC-KR")
            SingleChoiceDialog(
                title = stringResource(R.string.set_default_encoding),
                options = options,
                selectedIndex = options.indexOf(s.defaultEncoding).coerceAtLeast(0),
                onSelect = { i -> onUpdate { it.copy(defaultEncoding = options[i]) } },
                onDismiss = onDismiss,
            )
        }
        SettingChoice.LINE_BREAK -> {
            val labels = listOf(
                stringResource(R.string.linebreak_auto),
                "LF",
                "CRLF",
                "CR",
            )
            val values = listOf("Auto", "LF", "CRLF", "CR")
            SingleChoiceDialog(
                title = stringResource(R.string.set_line_break),
                options = labels,
                selectedIndex = values.indexOf(s.defaultLineEnding).coerceAtLeast(0),
                onSelect = { i -> onUpdate { it.copy(defaultLineEnding = values[i]) } },
                onDismiss = onDismiss,
            )
        }
        SettingChoice.INDENT -> {
            val labels = listOf(
                stringResource(R.string.indent_tab),
                stringResource(R.string.indent_2),
                stringResource(R.string.indent_4),
                stringResource(R.string.indent_8),
            )
            val values = listOf("\t", "  ", "    ", "        ")
            SingleChoiceDialog(
                title = stringResource(R.string.set_indent),
                options = labels,
                selectedIndex = values.indexOf(s.indentUnit).coerceAtLeast(0),
                onSelect = { i -> onUpdate { it.copy(indentUnit = values[i]) } },
                onDismiss = onDismiss,
            )
        }
        SettingChoice.SUGGESTIONS -> {
            val labels = listOf(
                stringResource(R.string.suggestions_on),
                stringResource(R.string.suggestions_off),
                stringResource(R.string.suggestions_off_aggressive),
            )
            val values = listOf(Suggestions.ON, Suggestions.OFF, Suggestions.OFF_AGGRESSIVE)
            SingleChoiceDialog(
                title = stringResource(R.string.set_suggestions),
                options = labels,
                selectedIndex = values.indexOf(s.suggestions).coerceAtLeast(0),
                onSelect = { i -> onUpdate { it.copy(suggestions = values[i]) } },
                onDismiss = onDismiss,
            )
        }
        SettingChoice.FONT_TYPE -> {
            val labels = listOf(
                stringResource(R.string.font_normal),
                stringResource(R.string.font_monospace),
            )
            SingleChoiceDialog(
                title = stringResource(R.string.set_font_type),
                options = labels,
                selectedIndex = if (s.monospace) 1 else 0,
                onSelect = { i -> onUpdate { it.copy(monospace = i == 1) } },
                onDismiss = onDismiss,
            )
        }
        SettingChoice.FONT_SIZE -> {
            val options = (10..28 step 2).map { it.toString() }
            SingleChoiceDialog(
                title = stringResource(R.string.set_font_size),
                options = options,
                selectedIndex = options.indexOf(s.fontSizeSp.toString()).coerceAtLeast(0),
                onSelect = { i -> onUpdate { it.copy(fontSizeSp = options[i].toInt()) } },
                onDismiss = onDismiss,
            )
        }
        SettingChoice.LINE_SPACING -> {
            val options = (0..8).map { it.toString() }
            SingleChoiceDialog(
                title = stringResource(R.string.set_line_spacing),
                options = options,
                selectedIndex = options.indexOf(s.lineSpacingExtra.toString()).coerceAtLeast(0),
                onSelect = { i -> onUpdate { it.copy(lineSpacingExtra = options[i].toInt()) } },
                onDismiss = onDismiss,
            )
        }
        SettingChoice.THEME -> {
            val labels = listOf(
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
                stringResource(R.string.theme_black),
            )
            val values = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.BLACK)
            SingleChoiceDialog(
                title = stringResource(R.string.set_theme),
                options = labels,
                selectedIndex = values.indexOf(s.appTheme).coerceAtLeast(0),
                onSelect = { i -> onUpdate { it.copy(appTheme = values[i]) } },
                onDismiss = onDismiss,
            )
        }
        SettingChoice.AUTOSAVE_INTERVAL -> {
            val options = listOf("1", "2", "5", "10")
            SingleChoiceDialog(
                title = stringResource(R.string.set_autosave_interval),
                options = options,
                selectedIndex = options.indexOf(s.autosaveIntervalMinutes.toString()).coerceAtLeast(0),
                onSelect = { i -> onUpdate { it.copy(autosaveIntervalMinutes = options[i].toInt()) } },
                onDismiss = onDismiss,
            )
        }
        null -> Unit
    }
}
