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
            checked = setOf(EditorMenuAction.WORD_WRAP),
            onMenu = {},
            showMenu = true,
            onMenuAction = {},
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

@Preview(showBackground = true)
@Composable
private fun EditorOverflowMenuPreview() {
    LunarisEditorTheme {
        EditorOverflowMenu(
            expanded = true,
            onDismiss = {},
            onAction = {},
            checked = setOf(EditorMenuAction.WORD_WRAP),
        )
    }
}
