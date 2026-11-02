package io.github.martindavinci.lunaris.scanner.parse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Text
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Wifi
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import org.junit.Test

class WifiContentTest {
    @Test fun typicalNetwork() {
        assertThat(parse("WIFI:T:WPA;S:Home;P:secret;;"))
            .isEqualTo(Wifi("Home", "secret", WifiSecurity.WPA, hidden = false))
    }

    @Test fun fieldsInAnyOrder() {
        assertThat(parse("WIFI:P:secret;H:true;S:Home;T:WEP;;"))
            .isEqualTo(Wifi("Home", "secret", WifiSecurity.WEP, hidden = true))
    }

    @Test fun prefixAndKeysAreCaseInsensitive() {
        assertThat(parse("wifi:t:wpa;s:Home;p:Secret;h:TRUE;;"))
            .isEqualTo(Wifi("Home", "Secret", WifiSecurity.WPA, hidden = true))
    }

    @Test fun noPassword() {
        assertThat(parse("WIFI:T:nopass;S:Cafe;;"))
            .isEqualTo(Wifi("Cafe", null, WifiSecurity.NONE, hidden = false))
        assertThat(parse("WIFI:T:NOPASS;S:Cafe;P:;;"))
            .isEqualTo(Wifi("Cafe", null, WifiSecurity.NONE, hidden = false))
        assertThat(parse("WIFI:S:Cafe;;"))
            .isEqualTo(Wifi("Cafe", null, WifiSecurity.NONE, hidden = false))
    }

    @Test fun nopassWinsButAStrayPasswordIsKeptSoThatItStaysMasked() {
        assertThat(parse("WIFI:T:nopass;S:Cafe;P:stray;;"))
            .isEqualTo(Wifi("Cafe", "stray", WifiSecurity.NONE, hidden = false))
    }

    @Test fun passwordWithoutTypeIsAssumedWpa() {
        assertThat(parse("WIFI:S:Home;P:secret;;"))
            .isEqualTo(Wifi("Home", "secret", WifiSecurity.WPA, hidden = false))
    }

    @Test fun unknownTypeFollowsThePassword() {
        assertThat(parse("WIFI:T:FOO;S:Home;P:x;;"))
            .isEqualTo(Wifi("Home", "x", WifiSecurity.WPA, hidden = false))
        assertThat(parse("WIFI:T:FOO;S:Home;;"))
            .isEqualTo(Wifi("Home", null, WifiSecurity.NONE, hidden = false))
    }

    @Test fun wpaWithoutAPasswordStaysWpa() {
        assertThat(parse("WIFI:T:WPA;S:Home;;"))
            .isEqualTo(Wifi("Home", null, WifiSecurity.WPA, hidden = false))
    }

    @Test fun wpaVariants() {
        listOf("WPA", "wpa2", "WPA3", "SAE", "WPA2-EAP").forEach { type ->
            assertThat(parse("WIFI:T:$type;S:Home;P:x;;"))
                .isEqualTo(Wifi("Home", "x", WifiSecurity.WPA, hidden = false))
        }
    }

    @Test fun escapedCharacters() {
        assertThat(parse("""WIFI:T:WPA;S:a\;b\,c\:d\"e\\f;P:p\;q\\;;""")).isEqualTo(
            Wifi("""a;b,c:d"e\f""", """p;q\""", WifiSecurity.WPA, hidden = false),
        )
    }

    @Test fun unknownEscapesKeepTheBackslash() {
        assertThat(parse("""WIFI:T:WPA;S:a\nb;P:C:\dir;;"""))
            .isEqualTo(Wifi("""a\nb""", """C:\dir""", WifiSecurity.WPA, hidden = false))
    }

    @Test fun colonInAValueNeedsNoEscape() {
        assertThat(parse("WIFI:T:WPA;S:a:b;P:c:d;;"))
            .isEqualTo(Wifi("a:b", "c:d", WifiSecurity.WPA, hidden = false))
    }

    @Test fun missingTerminatorsAreFine() {
        assertThat(parse("WIFI:T:WPA;S:Home;P:secret"))
            .isEqualTo(Wifi("Home", "secret", WifiSecurity.WPA, hidden = false))
    }

    @Test fun trailingBackslashDoesNotThrow() {
        assertThat(parse("""WIFI:S:Home\"""))
            .isEqualTo(Wifi("""Home\""", null, WifiSecurity.NONE, hidden = false))
    }

    @Test fun hiddenFlagOnlyWhenTrue() {
        assertThat((parse("WIFI:S:a;H:false;;") as Wifi).hidden).isFalse()
        assertThat((parse("WIFI:S:a;H:;;") as Wifi).hidden).isFalse()
        assertThat((parse("WIFI:S:a;H:true;;") as Wifi).hidden).isTrue()
    }

    @Test fun otherFieldsAndJunkAreIgnored() {
        assertThat(parse("WIFI:T:WPA2-EAP;E:PEAP;I:me;S:Corp;P:x;junk;;"))
            .isEqualTo(Wifi("Corp", "x", WifiSecurity.WPA, hidden = false))
    }

    @Test fun withoutAnSsidItIsText() {
        val malformed = listOf("WIFI:", "WIFI:;;", "WIFI:S:;T:WPA;;", "WIFI:S")
        malformed.forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
    }

    @Test fun withoutAnSsidAPasswordStillMakesItWifiSoThatItStaysMasked() {
        assertThat(parse("WIFI:T:WPA;P:x;;")).isEqualTo(Wifi("", "x", WifiSecurity.WPA, hidden = false))
        assertThat(parse("WIFI:S:;T:WPA;P:x;;")).isEqualTo(Wifi("", "x", WifiSecurity.WPA, hidden = false))
    }
}
