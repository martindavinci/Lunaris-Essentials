package tools.lunaris.texteditor.file

/**
 * Opaque reference to a file. In Phase 1 this wraps a SAF content:// URI string.
 * Kept as a value type so logic stays Android-free and unit-testable.
 */
@JvmInline
value class FileRef(val uri: String)
