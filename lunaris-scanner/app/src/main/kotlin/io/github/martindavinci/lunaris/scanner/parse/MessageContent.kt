package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.ParsedContent

private const val PHONE_PUNCTUATION = "+-(). *#,"
private const val PHONE_PARAMETER_START = ';'
private const val QUERY_SUBJECT = "SUBJECT"
private const val QUERY_BODY = "BODY"
private const val KEY_TO = "TO"
private const val KEY_SUBJECT = "SUB"
private const val KEY_BODY = "BODY"

internal fun parseMailto(body: String): ParsedContent.Email? {
    val query = queryParameters(body.substringAfter('?', ""))
    return email(
        to = percentDecode(body.substringBefore('?')),
        subject = query[QUERY_SUBJECT]?.let(::percentDecode),
        body = query[QUERY_BODY]?.let(::percentDecode),
    )
}

internal fun parseMatmsg(body: String): ParsedContent.Email? {
    val fields = parseFields(body)
    return email(
        to = fields.firstValue(KEY_TO).orEmpty(),
        subject = fields.firstValue(KEY_SUBJECT),
        body = fields.firstValue(KEY_BODY),
    )
}

/** The model always has a recipient, so a message without an address is not an email. */
private fun email(to: String, subject: String?, body: String?): ParsedContent.Email? {
    val address = to.trim()
    if (address.indexOf('@') !in 1 until address.lastIndex) return null
    return ParsedContent.Email(address, subject?.ifEmpty { null }, body?.ifEmpty { null })
}

internal fun parsePhone(body: String): ParsedContent.Phone? =
    percentDecode(body).takeIf(::isPhoneNumber)?.let(ParsedContent::Phone)

/**
 * `sms:number?body=encoded` and `smsto:number:text`. Text after the colon is plain, and may hold a
 * question mark, so whichever separator comes first decides the form.
 */
internal fun parseSms(body: String): ParsedContent.Sms? {
    val separator = body.indexOfFirst { it == ':' || it == '?' }
    val head = if (separator < 0) body else body.substring(0, separator)
    val number = percentDecode(head).trimEnd(PHONE_PARAMETER_START)
    if (!isPhoneNumber(number)) return null
    val message = when {
        separator < 0 -> null
        body[separator] == ':' -> body.substring(separator + 1)
        else -> queryParameters(body.substring(separator + 1))[QUERY_BODY]?.let(::percentDecode)
    }
    return ParsedContent.Sms(number, message?.ifEmpty { null })
}

private fun isPhoneNumber(text: String): Boolean {
    val number = text.substringBefore(PHONE_PARAMETER_START)
    return number.any { it.isAsciiDigit() } && number.all { it.isAsciiDigit() || it in PHONE_PUNCTUATION }
}
