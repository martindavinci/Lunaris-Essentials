package tools.lunaris.texteditor.feature.editor

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

private val sampleTabs = listOf(
    TabSummary(id = "1", title = "main.kt", dirty = true),
    TabSummary(id = "2", title = "README.md", dirty = false),
    TabSummary(id = "3", title = "build.gradle.kts", dirty = false),
)

@Preview(showBackground = true)
@Composable
private fun TabStripPreview() {
    LunarisEditorTheme {
        TabStrip(
            tabs = sampleTabs,
            activeTabId = "1",
            onSelect = {},
            onClose = {},
            onNew = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccessoryBarPreview() {
    LunarisEditorTheme {
        AccessoryBar(
            onKey = {},
            onCursorLeft = {},
            onCursorRight = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorTopBarPreview() {
    LunarisEditorTheme {
        EditorTopBar(
            title = "main.kt",
            subtitle = "kotlin · not saved",
            saveEnabled = true,
            onMenu = {},
            showMenu = true,
            onSearch = {},
            onSave = {},
            onOpenSheet = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorBottomBarPreview() {
    LunarisEditorTheme {
        EditorBottomBar(config = ToolbarConfig.DEFAULT, onItem = {})
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun EditorActionSheetPreview() {
    LunarisEditorTheme {
        tools.lunaris.texteditor.feature.editor.action.EditorActionSheet(
            checked = setOf(EditorMenuAction.WORD_WRAP),
            disabled = setOf(EditorMenuAction.RELOAD),
            onAction = {},
            onDismiss = {},
        )
    }
}
