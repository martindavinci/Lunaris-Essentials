package io.github.martindavinci.lunaris.pdf.url



object UrlSafety {
    private const val HTTPS = "https"
    private const val PUNYCODE_PREFIX = "xn--"
    private const val LAST_ASCII_CODE = 127
    private val usualPorts = setOf(80, 443)

    /** A URL that cannot be parsed only gets [UrlWarning.NOT_HTTPS]: nothing else about it can be told. */
    fun warnings(url: String): Set<UrlWarning> {
        val parts = UrlParts.parse(url) ?: return setOf(UrlWarning.NOT_HTTPS)
        return buildSet {
            if (parts.scheme != HTTPS) add(UrlWarning.NOT_HTTPS)
            if (parts.host.split('.').any { it.startsWith(PUNYCODE_PREFIX, ignoreCase = true) }) {
                add(UrlWarning.PUNYCODE)
            }
            if (parts.host.any { it.code > LAST_ASCII_CODE }) add(UrlWarning.NON_ASCII_HOST)
            if (parts.isIpAddress) add(UrlWarning.IP_ADDRESS_HOST)
            if (parts.hasCredentials) add(UrlWarning.EMBEDDED_CREDENTIALS)
            if (parts.port != null && parts.port !in usualPorts) add(UrlWarning.UNUSUAL_PORT)
        }
    }

    /** True only for well-formed http and https URLs. */
    fun isOpenable(url: String): Boolean = UrlParts.parse(url)?.isWeb == true
}
