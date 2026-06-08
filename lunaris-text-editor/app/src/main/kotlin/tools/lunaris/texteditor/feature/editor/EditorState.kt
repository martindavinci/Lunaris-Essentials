package tools.lunaris.texteditor.feature.editor

import tools.lunaris.texteditor.document.LineEnding
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class EditorState(
    val title: String = "untitled",
    val text: String = "",
    val fileRef: FileRef? = null,
    val charset: Charset = StandardCharsets.UTF_8,
    val lineEnding: LineEnding = LineEnding.LF,
    val dirty: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedSignal: Int = 0,
    val syntax: SyntaxSelection = SyntaxSelection.Auto,
    val scopeName: String? = null,
    val colorSchemeId: String = "",
)
