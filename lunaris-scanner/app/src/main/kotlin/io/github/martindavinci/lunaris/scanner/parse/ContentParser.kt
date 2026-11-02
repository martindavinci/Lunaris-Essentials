package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.ScannedCode

/** Reads what a scanned code means. It never throws: anything malformed is plain text. */
object ContentParser {
    private const val BEGIN_VCARD = "BEGIN:VCARD"
    private val beginEvent = listOf("BEGIN:VEVENT", "BEGIN:VCALENDAR")
    private val productFormats =
        setOf(CodeFormat.EAN_13, CodeFormat.EAN_8, CodeFormat.UPC_A, CodeFormat.UPC_E)

    private val prefixedParsers: List<Pair<String, (String) -> ParsedContent?>> = listOf(
        "WIFI:" to ::parseWifi,
        "MECARD:" to ::parseMeCard,
        "mailto:" to ::parseMailto,
        "MATMSG:" to ::parseMatmsg,
        "tel:" to ::parsePhone,
        "sms:" to ::parseSms,
        "smsto:" to ::parseSms,
        "geo:" to ::parseGeo,
    )

    fun parse(code: ScannedCode): ParsedContent {
        val text = code.text.trim()
        // The last line of defence: a stored code that broke a parser would break the history for good.
        return try {
            parseProduct(text, code.format) ?: parseText(text) ?: ParsedContent.Text(code.text)
        } catch (_: RuntimeException) {
            ParsedContent.Text(code.text)
        }
    }

    private fun parseProduct(text: String, format: CodeFormat): ParsedContent? =
        if (format in productFormats && text.isNotEmpty() && text.all { it.isAsciiDigit() }) {
            ParsedContent.Product(text, format)
        } else {
            null
        }

    private fun parseText(text: String): ParsedContent? {
        if (text.startsWith(BEGIN_VCARD, ignoreCase = true)) return parseVCard(text)
        if (beginEvent.any { text.startsWith(it, ignoreCase = true) }) return parseEvent(text)
        val (prefix, parser) = prefixedParsers.firstOrNull { (prefix, _) ->
            text.startsWith(prefix, ignoreCase = true)
        } ?: return parseUrl(text)
        return parser(text.substring(prefix.length))
    }
}
