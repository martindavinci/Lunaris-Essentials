package tools.lunaris.texteditor.document

import tools.lunaris.texteditor.file.FileRef
import tools.lunaris.texteditor.syntax.SyntaxSelection
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** One open tab. [dirty] is derived from [text] vs [savedText]. */
data class DocumentSession(
    val id: String,
    val title: String = UNTITLED,
    val fileRef: FileRef? = null,
    val text: String = "",
    val savedText: String = "",
    val charset: Charset = StandardCharsets.UTF_8,
    val lineEnding: LineEnding = LineEnding.LF,
    val syntax: SyntaxSelection = SyntaxSelection.Auto,
    val scopeName: String? = null,
    val readOnly: Boolean = false,
) {
    val dirty: Boolean get() = text != savedText

    fun markSaved(): DocumentSession = copy(savedText = text)

    companion object {
        /** Sentinel title for documents with no backing file. Localized only at display time. */
        const val UNTITLED = "untitled"
    }
}
