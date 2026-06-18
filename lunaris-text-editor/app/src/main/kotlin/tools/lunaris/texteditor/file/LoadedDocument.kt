package tools.lunaris.texteditor.file

import tools.lunaris.texteditor.document.LineEnding
import java.nio.charset.Charset

/** A file loaded into editor-ready text plus the metadata needed to save it back. */
data class LoadedDocument(
    val ref: FileRef,
    val name: String?,
    val text: String,
    val charset: Charset,
    val lineEnding: LineEnding,
    /** True if the file began with a BOM, so it can be re-emitted on save. */
    val hasBom: Boolean = false,
)
