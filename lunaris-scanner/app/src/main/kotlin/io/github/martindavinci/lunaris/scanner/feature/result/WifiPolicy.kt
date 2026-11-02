package io.github.martindavinci.lunaris.scanner.feature.result

import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

object WifiPolicy {
    private val PASSPHRASE_LENGTH = 8..63
    private const val MAX_ASCII = '\u007F'

    /**
     * Whether the system "add network" sheet can take this network. Suggestions have no WEP, and a
     * WPA passphrase outside the standard rules is refused: those are joined by hand instead.
     */
    fun canAddDirectly(wifi: ParsedContent.Wifi): Boolean = wifi.ssid.isNotEmpty() && when (wifi.security) {
        WifiSecurity.NONE -> true
        WifiSecurity.WEP -> false
        WifiSecurity.WPA -> wifi.password?.let(::isValidPassphrase) == true
    }

    private fun isValidPassphrase(password: String): Boolean =
        password.length in PASSPHRASE_LENGTH && password.all { it <= MAX_ASCII }
}
