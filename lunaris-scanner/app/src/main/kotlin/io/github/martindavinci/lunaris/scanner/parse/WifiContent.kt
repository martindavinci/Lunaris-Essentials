package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

private const val KEY_SSID = "S"
private const val KEY_TYPE = "T"
private const val KEY_PASSWORD = "P"
private const val KEY_HIDDEN = "H"
private const val TYPE_NONE = "nopass"
private const val TYPE_WEP = "WEP"
private val wpaTypePrefixes = listOf("WPA", "SAE")

/**
 * Whatever the network, a password in the code is kept: it is what masks the raw text, which spells
 * it out. So a code with a password and no SSID is still Wi-Fi, and an open network keeps a stray one.
 */
internal fun parseWifi(body: String): ParsedContent.Wifi? {
    val fields = parseFields(body)
    val ssid = fields.firstValue(KEY_SSID)
    val password = fields.firstValue(KEY_PASSWORD)
    if (ssid == null && password == null) return null
    return ParsedContent.Wifi(
        ssid = ssid.orEmpty(),
        password = password,
        security = securityOf(fields.firstValue(KEY_TYPE), password),
        hidden = fields.firstValue(KEY_HIDDEN).toBoolean(),
    )
}

/** A password is pointless on an open network, so a missing or unknown type with one means WPA. */
private fun securityOf(type: String?, password: String?): WifiSecurity = when {
    type.equals(TYPE_NONE, ignoreCase = true) -> WifiSecurity.NONE
    type.equals(TYPE_WEP, ignoreCase = true) -> WifiSecurity.WEP
    wpaTypePrefixes.any { type?.startsWith(it, ignoreCase = true) == true } -> WifiSecurity.WPA
    password != null -> WifiSecurity.WPA
    else -> WifiSecurity.NONE
}
