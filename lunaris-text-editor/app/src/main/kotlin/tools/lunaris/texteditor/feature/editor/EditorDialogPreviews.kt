package tools.lunaris.texteditor.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tools.lunaris.texteditor.data.Bookmark
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfigDialog
import tools.lunaris.texteditor.syntax.CharsetCatalog
import tools.lunaris.texteditor.syntax.ColorSchemeCatalog
import tools.lunaris.texteditor.syntax.ColorSchemeDefinition
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.LanguageDefinition
import tools.lunaris.texteditor.syntax.SyntaxSelection
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme
import java.nio.charset.StandardCharsets

private val sampleLanguageCatalog = LanguageCatalog(
    listOf(
        LanguageDefinition("kotlin", "Kotlin", "source.kotlin", "kotlin.json", listOf("kt", "kts")),
        LanguageDefinition("java", "Java", "source.java", "java.json", listOf("java")),
        LanguageDefinition("markdown", "Markdown", "text.html.markdown", "markdown.json", listOf("md")),
    )
)

private val sampleColorSchemeCatalog = ColorSchemeCatalog(
    listOf(
        ColorSchemeDefinition("dracula", "Dracula", "dracula.json", dark = true),
        ColorSchemeDefinition("solarized-light", "Solarized Light", "solarized-light.json", dark = false),
    ),
    requestedDefault = "dracula",
)

@Preview(showBackground = true)
@Composable
private fun SyntaxPickerDialogPreview() {
    LunarisEditorTheme {
        SyntaxPickerDialog(
            catalog = sampleLanguageCatalog,
            current = SyntaxSelection.Auto,
            onSelected = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ColorSchemePickerDialogPreview() {
    LunarisEditorTheme {
        ColorSchemePickerDialog(
            catalog = sampleColorSchemeCatalog,
            currentId = "dracula",
            onSelected = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EncodingPickerDialogPreview() {
    LunarisEditorTheme {
        EncodingPickerDialog(
            catalog = CharsetCatalog(),
            current = StandardCharsets.UTF_8,
            onSelected = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchDialogPreview() {
    LunarisEditorTheme {
        SearchDialog(
            onFind = { _, _ -> },
            onFindPrev = { _, _ -> },
            onReplace = { _, _, _ -> },
            onReplaceAll = { _, _, _ -> },
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GoToLineDialogPreview() {
    LunarisEditorTheme {
        GoToLineDialog(maxLine = 120, onGo = {}, onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsDialogPreview() {
    LunarisEditorTheme {
        StatsDialog(words = 842, characters = 5120, onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun InsertColorDialogPreview() {
    LunarisEditorTheme {
        InsertColorDialog(onPick = {}, onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun BookmarksDialogPreview() {
    LunarisEditorTheme {
        BookmarksDialog(
            bookmarks = listOf(
                Bookmark(uri = "content://doc/1", name = "TODO", line = 42),
                Bookmark(uri = "content://doc/2", name = "Bugfix", line = 108),
            ),
            onOpen = {},
            onRemove = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecentFilesDialogPreview() {
    LunarisEditorTheme {
        RecentFilesDialog(
            recents = listOf(
                RecentEntry(uri = "content://doc/1", name = "main.kt"),
                RecentEntry(uri = "content://doc/2", name = "notes.md"),
            ),
            onOpen = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolbarConfigDialogPreview() {
    LunarisEditorTheme {
        ToolbarConfigDialog(
            initial = ToolbarConfig.DEFAULT,
            onApply = {},
            onDismiss = {},
        )
    }
}
