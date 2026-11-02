package io.github.martindavinci.lunaris.scanner.encode

import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import java.math.BigDecimal

/** Builds the text that goes inside a code, in the forms phone cameras and scanner apps understand. */
object CodePayloads {
    private const val CRLF = "\r\n"
    private const val WIFI_SPECIAL_CHARACTERS = "\\;,:\""
    private const val PHONE_VISUAL_SEPARATORS = "().-"
    private const val URI_UNRESERVED_SYMBOLS = "-._~"
    private const val EMAIL_ADDRESS_SYMBOLS = "@,"
    private const val HEX_DIGITS = "0123456789ABCDEF"
    private const val BYTE_MASK = 0xFF
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0xF

    fun wifi(ssid: String, password: String?, security: WifiSecurity, hidden: Boolean): String = buildString {
        append("WIFI:T:").append(wifiToken(security)).append(";S:").append(escapeWifi(ssid)).append(';')
        if (security != WifiSecurity.NONE && !password.isNullOrEmpty()) {
            append("P:").append(escapeWifi(password)).append(';')
        }
        if (hidden) append("H:true;")
        append(';')
    }

    /** vCard 3.0. N and FN are mandatory in that version, so they are written even when empty. */
    fun contact(contact: ParsedContent.Contact): String {
        val name = contact.name.orEmpty().trim()
        val lines = buildList {
            add("BEGIN:VCARD")
            add("VERSION:3.0")
            add("N:${structuredName(name)}")
            add("FN:${escapeVCard(name)}")
            textLine("ORG", contact.organization)?.let(::add)
            textLine("TITLE", contact.title)?.let(::add)
            contact.phones.mapNotNull { textLine("TEL", it) }.let(::addAll)
            contact.emails.mapNotNull { textLine("EMAIL", it) }.let(::addAll)
            contact.address?.takeIf { it.isNotBlank() }?.let { add("ADR:;;${escapeVCard(it)};;;;") }
            contact.website?.takeIf { it.isNotBlank() }?.let { add("URL:${withoutLineBreaks(it.trim())}") }
            textLine("NOTE", contact.note)?.let(::add)
            add("END:VCARD")
        }
        return lines.joinToString(CRLF, postfix = CRLF)
    }

    fun email(to: String, subject: String?, body: String?): String {
        val query = listOfNotNull(
            subject?.takeIf { it.isNotEmpty() }?.let { "subject=${percentEncode(it)}" },
            body?.takeIf { it.isNotEmpty() }?.let { "body=${percentEncode(withCrlfLines(it))}" },
        )
        val address = percentEncode(to.trim(), EMAIL_ADDRESS_SYMBOLS)
        return if (query.isEmpty()) "mailto:$address" else "mailto:$address?${query.joinToString("&")}"
    }

    /** A # left as it is would start a URI fragment, and the dialer would lose it. */
    fun phone(number: String): String = "tel:${dialable(number).replace("#", "%23")}"

    fun sms(number: String, body: String?): String =
        if (body.isNullOrEmpty()) "SMSTO:${dialable(number)}" else "SMSTO:${dialable(number)}:$body"

    fun geo(latitude: Double, longitude: Double): String = "geo:${plainDecimal(latitude)},${plainDecimal(longitude)}"

    private fun wifiToken(security: WifiSecurity): String = when (security) {
        WifiSecurity.NONE -> "nopass"
        WifiSecurity.WEP -> "WEP"
        WifiSecurity.WPA -> "WPA"
    }

    private fun escapeWifi(value: String): String = buildString {
        value.forEach { char ->
            if (char in WIFI_SPECIAL_CHARACTERS) append('\\')
            append(char)
        }
    }

    private fun textLine(property: String, value: String?): String? =
        value?.takeIf { it.isNotBlank() }?.let { "$property:${escapeVCard(it)}" }

    /** Family name last, the rest as given names: the name field is one string, so this is a best guess. */
    private fun structuredName(name: String): String {
        val family = name.substringAfterLast(' ').trim()
        val given = name.substringBeforeLast(' ', missingDelimiterValue = "").trim()
        return "${escapeVCard(family)};${escapeVCard(given)};;;"
    }

    private fun escapeVCard(value: String): String = withLfLines(value.trim())
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    private fun withLfLines(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n')

    private fun withCrlfLines(value: String): String = withLfLines(value).replace("\n", CRLF)

    private fun withoutLineBreaks(value: String): String = value.filterNot { it == '\r' || it == '\n' }

    private fun dialable(number: String): String =
        number.filterNot { it.isWhitespace() || it in PHONE_VISUAL_SEPARATORS }

    private fun plainDecimal(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun percentEncode(value: String, keep: String = ""): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val code = byte.toInt() and BYTE_MASK
            val char = code.toChar()
            if (isUriUnreserved(char) || char in keep) {
                append(char)
            } else {
                append('%').append(HEX_DIGITS[code shr NIBBLE_BITS]).append(HEX_DIGITS[code and NIBBLE_MASK])
            }
        }
    }

    private fun isUriUnreserved(char: Char): Boolean =
        char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in URI_UNRESERVED_SYMBOLS
}
