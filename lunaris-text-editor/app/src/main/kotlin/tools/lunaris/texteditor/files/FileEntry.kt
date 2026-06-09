package tools.lunaris.texteditor.files

/** One entry in a browsed directory. [ref] is a path (All-files) or a content:// uri (SAF). */
data class FileEntry(
    val name: String,
    val ref: String,
    val isDirectory: Boolean,
    val extension: String,
)
