package tools.lunaris.texteditor.feature.settings

/** "=====" and "-----" rules only frame the plain-text file; on screen they would just wrap. */
private val RULE_LINE = Regex("""^\s*[=-]{10,}\s*$""")

/** A line that starts a new item rather than continuing the previous one. */
private val ITEM_START = Regex("""^\s*([-*•]\s|\d+\.\s|\(?[a-z0-9]\)\s|[A-Z][A-Za-z ]{0,20}:\s)""")

/**
 * Splits a plain-text notices file into paragraphs and undoes the hard line wrapping of the
 * license texts, so they reflow to the screen width. List items and "Label: value" lines keep
 * their own line.
 */
internal fun noticeParagraphs(text: String): List<String> =
    text.lines()
        .filterNot { RULE_LINE.matches(it) }
        .joinToString("\n")
        .split(Regex("""\n\s*\n"""))
        .map { it.trim('\n') }
        .filter { it.isNotBlank() }
        .map(::reflow)

private fun reflow(paragraph: String): String {
    val lines = paragraph.lines()
    return buildString {
        lines.forEachIndexed { i, line ->
            when {
                i == 0 -> append(line.trimEnd())
                ITEM_START.containsMatchIn(line) -> append('\n').append(line.trimEnd())
                else -> append(' ').append(line.trim())
            }
        }
    }
}
