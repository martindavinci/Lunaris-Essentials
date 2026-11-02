package io.github.martindavinci.lunaris.scanner.parse

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

private const val ESCAPE = '\\'
private const val FIELD_ESCAPABLE = "\\;,:\""
private const val NEWLINE_ESCAPES = "nN"
private const val PERCENT = '%'
private const val HEX_RADIX = 16
private const val HEX_BITS = 4
private const val HEX_ESCAPE_LENGTH = 3

/** Splits on [separator] where no backslash escapes it. The pieces keep their escapes. */
internal fun String.splitUnescaped(separator: Char): List<String> {
    val pieces = mutableListOf<String>()
    var start = 0
    var index = 0
    while (index < length) {
        when (this[index]) {
            ESCAPE -> index += 2
            separator -> {
                pieces += substring(start, index)
                index++
                start = index
            }
            else -> index++
        }
    }
    pieces += substring(start)
    return pieces
}

/** The escapes of WIFI:, MECARD: and MATMSG: values. Any other backslash stays as written. */
internal fun unescapeField(raw: String): String = raw.unescape { it.takeIf { it in FIELD_ESCAPABLE } }

/** The escapes of vCard and iCalendar text, which add `\n` for a line break. */
internal fun unescapeText(raw: String): String = raw.unescape { character ->
    when (character) {
        in NEWLINE_ESCAPES -> '\n'
        in FIELD_ESCAPABLE -> character
        else -> null
    }
}

private inline fun String.unescape(replacement: (Char) -> Char?): String {
    val out = StringBuilder(length)
    var index = 0
    while (index < length) {
        val character = this[index]
        val replaced = if (character == ESCAPE) getOrNull(index + 1)?.let(replacement) else null
        out.append(replaced ?: character)
        index += if (replaced == null) 1 else 2
    }
    return out.toString()
}

internal fun percentDecode(text: String): String = decodeHexEscapes(text, PERCENT, Charsets.UTF_8)

/**
 * Decodes each run of [marker] + two hex digits as bytes in [charset]. Anything else stays literal.
 * Only ever decodes: not every charset can encode, and encoding would throw for those.
 */
internal fun decodeHexEscapes(text: String, marker: Char, charset: Charset): String {
    val out = StringBuilder(text.length)
    val run = ByteArrayOutputStream()
    fun flushRun() {
        if (run.size() > 0) out.append(String(run.toByteArray(), charset))
        run.reset()
    }
    var index = 0
    while (index < text.length) {
        val byte = if (text[index] == marker) hexByteAt(text, index + 1) else null
        if (byte != null) {
            run.write(byte)
            index += HEX_ESCAPE_LENGTH
        } else {
            flushRun()
            out.append(text[index])
            index++
        }
    }
    flushRun()
    return out.toString()
}

private fun hexByteAt(text: String, index: Int): Int? {
    val high = text.getOrNull(index)?.digitToIntOrNull(HEX_RADIX) ?: return null
    val low = text.getOrNull(index + 1)?.digitToIntOrNull(HEX_RADIX) ?: return null
    return (high shl HEX_BITS) or low
}

/** The parameters of a URI query, keys in upper case, values still percent-encoded. First one wins. */
internal fun queryParameters(query: String): Map<String, String> = buildMap {
    query.split('&').forEach { pair ->
        putIfAbsent(pair.substringBefore('=').uppercase(), pair.substringAfter('=', ""))
    }
}

/** A `KEY:value` piece of a WIFI:, MECARD: or MATMSG: payload. */
internal class Field(val key: String, val raw: String) {
    val value: String get() = unescapeField(raw)
}

internal fun parseFields(body: String): List<Field> = body.splitUnescaped(';').mapNotNull { piece ->
    val colon = piece.indexOf(':')
    if (colon < 0) null else Field(piece.substring(0, colon).trim().uppercase(), piece.substring(colon + 1))
}

/** The first value of [key] that is not empty. */
internal fun List<Field>.firstValue(key: String): String? =
    firstNotNullOfOrNull { field -> field.value.takeIf { field.key == key && it.isNotEmpty() } }
