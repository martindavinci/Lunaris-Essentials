package tools.lunaris.texteditor.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.settings.EditorSettings
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    LunarisEditorTheme {
        SettingsContent(
            s = EditorSettings(),
            fileFilterOn = true,
            toolbarConfig = ToolbarConfig.DEFAULT,
            onBack = {},
            onUpdate = {},
            onSetFileFilter = {},
            onSetToolbar = {},
            onOpenAssociations = {},
            onOpenLicenses = {},
            onBackup = {},
            onRestore = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsKitPreview() {
    LunarisEditorTheme {
        Column {
            SettingsSectionHeader("Display")
            SettingsToggleRow("Line numbers", "Show line gutter", checked = true) {}
            SettingsChoiceRow("Font size", "16", onClick = {})
            SettingsNavRow("Licenses", onClick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SingleChoiceDialogPreview() {
    LunarisEditorTheme {
        SingleChoiceDialog(
            title = "Theme",
            options = listOf("Light", "Dark", "Black"),
            selectedIndex = 1,
            onSelect = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LicensesScreenPreview() {
    LunarisEditorTheme {
        LicensesScreen(onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun AssocRowPreview() {
    LunarisEditorTheme {
        AssocRow(extension = "kt", languageDisplay = "Kotlin", onDelete = {})
    }
}

/** Component-level preview: the FileAssociationScreen body is VM-driven; the
 *  stateless top bar + row are previewed instead of the full screen. */
@Preview(showBackground = true)
@Composable
private fun AssocTopBarPreview() {
    LunarisEditorTheme {
        AssocTopBar(onBack = {})
    }
}
