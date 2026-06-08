package tools.lunaris.texteditor.document

enum class LineEnding(val sequence: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r");

    companion object {
        /** Detects the dominant line ending from raw text containing original separators. */
        fun detect(raw: String): LineEnding = when {
            raw.contains("\r\n") -> CRLF
            raw.contains("\r") -> CR
            else -> LF
        }
    }
}
