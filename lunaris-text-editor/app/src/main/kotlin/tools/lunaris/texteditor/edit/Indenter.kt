package tools.lunaris.texteditor.edit

object Indenter {
    fun increase(text: String, selStart: Int, selEnd: Int, unit: String): String =
        transformLines(text, selStart, selEnd) { unit + it }

    fun decrease(text: String, selStart: Int, selEnd: Int, unit: String): String =
        transformLines(text, selStart, selEnd) { line ->
            when {
                line.startsWith(unit) -> line.removePrefix(unit)
                line.startsWith("\t") -> line.removePrefix("\t")
                else -> line
            }
        }

    private fun transformLines(text: String, selStart: Int, selEnd: Int, op: (String) -> String): String {
        val lines = text.split("\n")
        val startLine = lineAtOffset(text, selStart.coerceIn(0, text.length))
        val endLine = lineAtOffset(text, selEnd.coerceIn(0, text.length))
        val lo = minOf(startLine, endLine)
        val hi = maxOf(startLine, endLine)
        return lines.mapIndexed { i, line -> if (i in lo..hi) op(line) else line }.joinToString("\n")
    }

    private fun lineAtOffset(text: String, offset: Int): Int =
        text.substring(0, offset).count { it == '\n' }
}
