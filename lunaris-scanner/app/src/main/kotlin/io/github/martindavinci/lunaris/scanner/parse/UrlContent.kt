package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.ParsedContent

private const val BARE_DOMAIN_SCHEME = "https://"
private const val HOST_END = "/?#:"
private const val MIN_DOMAIN_LABELS = 2
private const val MIN_TOP_LEVEL_LENGTH = 2

/**
 * The scheme comes out lowercase: Android matches intent filters case-sensitively, and codes
 * written in the QR alphanumeric mode are often all capitals.
 */
internal fun parseUrl(text: String): ParsedContent.Url? {
    val parts = UrlParts.parse(text) ?: return parseBareDomain(text)
    if (!parts.isWeb) return null
    return ParsedContent.Url(parts.scheme + text.substring(parts.scheme.length))
}

private fun parseBareDomain(text: String): ParsedContent.Url? {
    val labels = text.takeWhile { it !in HOST_END }.split('.')
    if (labels.size < MIN_DOMAIN_LABELS || !labels.all(::isDomainLabel)) return null
    if (!isTopLevelLabel(labels.last())) return null
    val url = BARE_DOMAIN_SCHEME + text
    val parts = UrlParts.parse(url) ?: return null
    return if (parts.hasCredentials) null else ParsedContent.Url(url)
}

private fun isDomainLabel(label: String): Boolean =
    label.isNotEmpty() && label.all { it.isAsciiLetter() || it.isAsciiDigit() || it == '-' }

private fun isTopLevelLabel(label: String): Boolean =
    label.length >= MIN_TOP_LEVEL_LENGTH && label.all { it.isAsciiLetter() }
