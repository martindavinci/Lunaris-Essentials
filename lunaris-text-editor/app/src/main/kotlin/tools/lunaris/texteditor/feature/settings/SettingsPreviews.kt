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
            SettingsToggleRow("Line numbers", "Show line gutter", checked = true) {}
            SettingsChoiceRow("Font size", "16", onClick = {})
            SettingsNavRow("Licenses", onClick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsHubPreview() {
    LunarisEditorTheme {
        SettingsHub(onOpen = {})
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
private fun EditorCategoryPreview() {
    LunarisEditorTheme {
        Column {
            EditorCategory(
                s = EditorSettings(),
                onUpdate = {},
                onIndentClick = {},
                onLineBreakClick = {},
                onShowToolbar = {},
            )
        }
    }
}

