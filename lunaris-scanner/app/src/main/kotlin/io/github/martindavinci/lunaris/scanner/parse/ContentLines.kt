package io.github.martindavinci.lunaris.scanner.parse

import java.nio.charset.Charset

private const val QUOTED_PRINTABLE = "QUOTED-PRINTABLE"
private const val QUOTED_PRINTABLE_MARKER = '='
private const val CHARSET_PARAMETER = "CHARSET="
private const val LINE_FOLD_CHARACTERS = " \t"

internal const val PROPERTY_BEGIN = "BEGIN"
internal const val PROPERTY_END = "END"

/** One property of a vCard or iCalendar text: `group.NAME;PARAM=x:value`. Its value is still escaped. */
internal class ContentLine(val name: String, val value: String, private val parameters: List<String> = emptyList()) {
    /** The value of the parameter [name], without quotes. */
    fun parameter(name: String): String? =
        parameters.firstOrNull { it.substringBefore('=').trim().equals(name, ignoreCase = true) }
            ?.substringAfter('=', "")?.trim()?.trim('"')

    fun isBegin(component: String): Boolean = isMarker(PROPERTY_BEGIN, component)

    fun isEnd(component: String): Boolean = isMarker(PROPERTY_END, component)

    private fun isMarker(marker: String, component: String): Boolean =
        name == marker && value.trim().equals(component, ignoreCase = true)
}

/** Lines that have no colon outside double quotes are not properties and are skipped. */
internal fun parseContentLines(text: String): List<ContentLine> = unfold(text).mapNotNull(::parseLine)

/** The first [name] whose unescaped text is not blank. */
internal fun List<ContentLine>.firstText(name: String): String? =
    allTexts(name).firstOrNull()

/** Every [name] whose unescaped text is not blank. */
internal fun List<ContentLine>.allTexts(name: String): List<String> =
    filter { it.name == name }.map { unescapeText(it.value).trim() }.filter { it.isNotEmpty() }

private fun unfold(text: String): List<String> {
    val lines = mutableListOf<String>()
    text.lineSequence().forEach { line ->
        val previous = lines.lastOrNull()
        when {
            previous != null && hasSoftLineBreak(previous) -> lines[lines.lastIndex] = previous.dropLast(1) + line
            previous != null && line.firstOrNull()?.let { it in LINE_FOLD_CHARACTERS } == true ->
                lines[lines.lastIndex] = previous + line.drop(1)
            else -> lines += line
        }
    }
    return lines
}

private fun hasSoftLineBreak(line: String): Boolean =
    line.endsWith(QUOTED_PRINTABLE_MARKER) &&
        line.substringBefore(':').contains(QUOTED_PRINTABLE, ignoreCase = true)

private fun parseLine(line: String): ContentLine? {
    val colon = indexOfColonOutsideQuotes(line)
    if (colon < 0) return null
    val head = line.substring(0, colon).split(';')
    val value = line.substring(colon + 1)
    val parameters = head.drop(1)
    val name = head.first().trim().substringAfterLast('.').uppercase()
    val decoded = if (isQuotedPrintable(parameters)) decodeQuotedPrintable(value, parameters) else value
    return ContentLine(name, decoded, parameters)
}

private fun indexOfColonOutsideQuotes(line: String): Int {
    var quoted = false
    line.forEachIndexed { index, character ->
        if (character == '"') quoted = !quoted
        if (character == ':' && !quoted) return index
    }
    return -1
}

private fun isQuotedPrintable(parameters: List<String>): Boolean = parameters.any {
    it.substringAfter('=').equals(QUOTED_PRINTABLE, ignoreCase = true)
}

private fun decodeQuotedPrintable(value: String, parameters: List<String>): String =
    decodeHexEscapes(value, QUOTED_PRINTABLE_MARKER, charsetOf(parameters))

private fun charsetOf(parameters: List<String>): Charset {
    val name = parameters.firstOrNull { it.startsWith(CHARSET_PARAMETER, ignoreCase = true) }
        ?.substring(CHARSET_PARAMETER.length)
        ?: return Charsets.UTF_8
    return try {
        Charset.forName(name)
    } catch (_: IllegalArgumentException) {
        Charsets.UTF_8
    }
}
