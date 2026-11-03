package io.github.martindavinci.lunaris.pdf.url

import kotlin.text.CharCategory.COMBINING_SPACING_MARK
import kotlin.text.CharCategory.FORMAT
import kotlin.text.CharCategory.NON_SPACING_MARK

private const val SCHEME_SEPARATOR = "://"
private const val AUTHORITY_END = "/?#"
private const val HOST_PUNCTUATION = "-_"
private const val IPV6_PUNCTUATION = ":."
private const val HEX_PREFIX = "0x"
private const val HEX_RADIX = 16
private const val MIN_PORT = 1
private const val MAX_PORT = 65_535

/**
 * The parts of a `scheme://authority` URL that decide where it really leads.
 *
 * Hand written because java.net.URI gives no host for names such as `exa_mple.com` or `bücher.de`
 * and refuses characters browsers accept in a path. Whatever a browser could read differently
 * (white space, control characters, a backslash in the authority) is rejected here, and so are
 * invisible format characters, which can make the text on screen hide the real host.
 */
data class UrlParts(
    val scheme: String,
    val hasCredentials: Boolean,
    val host: String,
    val port: Int?,
) {
    val isWeb: Boolean get() = scheme == "http" || scheme == "https"

    /** Browsers read a host whose last label is a number as an IPv4 address, such as `2130706433`. */
    val isIpAddress: Boolean
        get() = host.startsWith('[') || isNumericLabel(host.removeSuffix(".").substringAfterLast('.'))

    companion object {
        fun parse(url: String): UrlParts? {
            if (url.any { it.isWhitespace() || it.isISOControl() || it.category == FORMAT }) return null
            val scheme = url.substringBefore(SCHEME_SEPARATOR, "").takeIf(::isScheme) ?: return null
            val authority = url.substring(scheme.length + SCHEME_SEPARATOR.length)
                .takeWhile { it !in AUTHORITY_END }
            if ('\\' in authority) return null
            val hostAndPort = authority.substringAfterLast('@')
            val host = hostOf(hostAndPort).takeIf(::isHost) ?: return null
            val portText = hostAndPort.substring(host.length)
            val port = if (portText.isEmpty()) null else parsePort(portText) ?: return null
            return UrlParts(scheme.lowercase(), '@' in authority, host, port)
        }

        private fun isScheme(text: String): Boolean =
            text.firstOrNull()?.isAsciiLetter() == true &&
                text.all { it.isAsciiLetter() || it.isAsciiDigit() || it in "+-." }

        private fun hostOf(hostAndPort: String): String =
            if (hostAndPort.startsWith('[')) {
                hostAndPort.substringBefore(']', "") + "]"
            } else {
                hostAndPort.substringBefore(':')
            }

        private fun isHost(host: String): Boolean =
            if (host.startsWith('[')) {
                isIpv6Literal(host.removeSurrounding("[", "]"))
            } else {
                host.removeSuffix(".").split('.').all { label ->
                    label.isNotEmpty() && label.all(::isHostCharacter)
                }
            }

        private fun isIpv6Literal(text: String): Boolean =
            ':' in text && text.all { it in IPV6_PUNCTUATION || it.digitToIntOrNull(HEX_RADIX) != null }

        private fun isHostCharacter(character: Char): Boolean =
            character.isLetterOrDigit() || character in HOST_PUNCTUATION ||
                character.category == NON_SPACING_MARK || character.category == COMBINING_SPACING_MARK

        private fun parsePort(text: String): Int? {
            val digits = text.removePrefix(":")
            if (digits.length == text.length || digits.isEmpty()) return null
            if (!digits.all { it.isAsciiDigit() }) return null
            return digits.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
        }

        private fun isNumericLabel(label: String): Boolean {
            if (label.startsWith(HEX_PREFIX, ignoreCase = true)) {
                return label.drop(HEX_PREFIX.length).all { it.digitToIntOrNull(HEX_RADIX) != null }
            }
            return label.isNotEmpty() && label.all { it.isAsciiDigit() }
        }
    }
}

fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
