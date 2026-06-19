package tools.lunaris.texteditor.file

/**
 * Thrown when a file is too big to load fully into memory. The whole document is held as a String,
 * so reads are bounded to [limitBytes]; this fires both from an up-front size check and from the
 * bounded read itself (for providers that don't report a size).
 */
class FileTooLargeException(val limitBytes: Long) :
    java.io.IOException("File exceeds the $limitBytes-byte open limit")
