package tools.lunaris.texteditor.feature.editor

import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.document.LineEnding
import tools.lunaris.texteditor.feature.editor.toolbar.ToolbarConfig
import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Categorizes a transient editor error so the UI can show a localized message. */
enum class EditorErrorKind {
    OPEN, SAVE, TOO_LARGE, NOT_FOUND,
    /** Not a failure: the save succeeded, but switched the file to UTF-8 to keep every character. */
    SAVED_AS_UTF8,
}

data class EditorUiState(
    val tabs: List<TabSummary> = emptyList(),
    val activeTabId: String = "",
    val title: String = DocumentSession.UNTITLED,
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
    val errorKind: EditorErrorKind? = null,
    /** A uri whose access has expired — the UI offers to re-pick it to restore the grant. */
    val reauthorizeUri: String? = null,
    val savedSignal: Int = 0,
    /** Bumped when an in-place save can't write back (read-only source) so the UI offers Save As. */
    val saveAsRequest: Int = 0,
    val wordWrap: Boolean = true,
    val readOnly: Boolean = false,
    val toolbar: ToolbarConfig = ToolbarConfig.DEFAULT,
    val pendingGoToLine: Int? = null,
    /** One-shot caret offset to restore after a document loads (e.g. reopening a closed file). */
    val pendingGoToOffset: Int? = null,
)
