package tools.lunaris.texteditor.feature.editor

import tools.lunaris.texteditor.document.LineEnding
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class EditorUiState(
    val tabs: List<TabSummary> = emptyList(),
    val activeTabId: String = "",
    val title: String = "untitled",
    val text: String = "",
    val fileRef: FileRef? = null,
    val charset: Charset = StandardCharsets.UTF_8,
    val lineEnding: LineEnding = LineEnding.LF,
    val syntax: SyntaxSelection = SyntaxSelection.Auto,
    val scopeName: String? = null,
    val dirty: Boolean = false,
    val colorSchemeId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedSignal: Int = 0,
    val wordWrap: Boolean = true,
    val readOnly: Boolean = false,
    val toolbar: ToolbarConfig = ToolbarConfig.DEFAULT,
)
