package io.github.martindavinci.lunaris.scanner.feature.result

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import org.junit.Test

class WifiPolicyTest {
    private fun wifi(security: WifiSecurity, password: String?, ssid: String = "home") =
        ParsedContent.Wifi(ssid, password, security, hidden = false)

    @Test fun openNetwork_isAddedDirectly() {
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.NONE, null))).isTrue()
    }

    @Test fun wep_isNotSupportedBySuggestions() {
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.WEP, "12345"))).isFalse()
    }

    @Test fun wpa_needsAPassphraseOf8To63AsciiCharacters() {
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.WPA, "a".repeat(8)))).isTrue()
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.WPA, "a".repeat(63)))).isTrue()
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.WPA, "a".repeat(7)))).isFalse()
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.WPA, "a".repeat(64)))).isFalse()
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.WPA, "pässwörd123"))).isFalse()
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.WPA, null))).isFalse()
    }

    @Test fun emptySsid_isNotAddedDirectly() {
        assertThat(WifiPolicy.canAddDirectly(wifi(WifiSecurity.NONE, null, ssid = ""))).isFalse()
    }
}
