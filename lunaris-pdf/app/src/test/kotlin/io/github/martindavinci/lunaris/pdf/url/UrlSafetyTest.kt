package io.github.martindavinci.lunaris.pdf.url

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlSafetyTest {
    private fun warnings(url: String) = UrlSafety.warnings(url)

    @Test fun plainHttpsHasNoWarnings() {
        assertThat(warnings("https://example.com")).isEmpty()
        assertThat(warnings("https://example.com/a/b?c=d#e")).isEmpty()
        assertThat(warnings("HTTPS://EXAMPLE.COM/")).isEmpty()
    }

    @Test fun everythingElseIsNotHttps() {
        assertThat(warnings("http://example.com")).containsExactly(UrlWarning.NOT_HTTPS)
        assertThat(warnings("HTTP://example.com")).containsExactly(UrlWarning.NOT_HTTPS)
        assertThat(warnings("ftp://example.com")).containsExactly(UrlWarning.NOT_HTTPS)
        assertThat(warnings("javascript:alert(1)")).containsExactly(UrlWarning.NOT_HTTPS)
    }

    @Test fun punycodeLabelsAnywhereInTheHost() {
        assertThat(warnings("https://xn--pple-43d.com")).containsExactly(UrlWarning.PUNYCODE)
        assertThat(warnings("https://shop.XN--pple-43d.com/")).containsExactly(UrlWarning.PUNYCODE)
    }

    @Test fun punycodeOutsideTheHostIsFine() {
        assertThat(warnings("https://example.com/xn--pple-43d")).isEmpty()
        assertThat(warnings("https://not-xn--a.example.com")).isEmpty()
    }

    @Test fun nonAsciiHost() {
        val cyrillicLookalike = "https://аpple.com"
        assertThat(warnings(cyrillicLookalike)).containsExactly(UrlWarning.NON_ASCII_HOST)
        assertThat(warnings("https://bücher.example")).containsExactly(UrlWarning.NON_ASCII_HOST)
    }

    @Test fun nonAsciiOutsideTheHostIsFine() {
        assertThat(warnings("https://example.com/caffè?q=日本")).isEmpty()
    }

    @Test fun ipAddressHosts() {
        val ipHosts = listOf(
            "192.168.1.1",
            "127.0.0.1",
            "[::1]",
            "[2001:db8::1]",
            "2130706433",
            "0x7f.0.0.1",
            "0X7F000001",
            "127.1",
            "1.1.1.1.",
        )
        ipHosts.forEach { host ->
            assertThat(warnings("https://$host/")).containsExactly(UrlWarning.IP_ADDRESS_HOST)
        }
    }

    @Test fun hostsWithDigitsThatAreNotAddresses() {
        assertThat(warnings("https://example1.com")).isEmpty()
        assertThat(warnings("https://1.example.com")).isEmpty()
        assertThat(warnings("https://1password.com")).isEmpty()
        assertThat(warnings("https://0xabc.example.org")).isEmpty()
    }

    @Test fun embeddedCredentials() {
        val expected = UrlWarning.EMBEDDED_CREDENTIALS
        assertThat(warnings("https://user:pass@example.com")).containsExactly(expected)
        assertThat(warnings("https://user@example.com/")).containsExactly(expected)
        assertThat(warnings("https://bank.example.com@evil.example/")).containsExactly(expected)
        assertThat(warnings("https://a@b@example.com")).containsExactly(expected)
        assertThat(warnings("https://@example.com")).containsExactly(expected)
    }

    @Test fun atSignOutsideTheAuthorityIsFine() {
        assertThat(warnings("https://example.com/@user")).isEmpty()
        assertThat(warnings("https://example.com?who=a@b")).isEmpty()
        assertThat(warnings("https://example.com#a@b")).isEmpty()
    }

    @Test fun unusualPort() {
        assertThat(warnings("https://example.com:8443/")).containsExactly(UrlWarning.UNUSUAL_PORT)
        assertThat(warnings("https://example.com:1")).containsExactly(UrlWarning.UNUSUAL_PORT)
        assertThat(warnings("https://[::1]:8443/"))
            .containsExactly(UrlWarning.IP_ADDRESS_HOST, UrlWarning.UNUSUAL_PORT)
    }

    @Test fun usualPorts() {
        assertThat(warnings("https://example.com:443/")).isEmpty()
        assertThat(warnings("https://example.com:80/")).isEmpty()
        assertThat(warnings("http://example.com:80/")).containsExactly(UrlWarning.NOT_HTTPS)
    }

    @Test fun warningsAddUp() {
        assertThat(warnings("http://user:pw@10.0.0.1:8080/login")).containsExactly(
            UrlWarning.NOT_HTTPS,
            UrlWarning.EMBEDDED_CREDENTIALS,
            UrlWarning.IP_ADDRESS_HOST,
            UrlWarning.UNUSUAL_PORT,
        )
    }

    @Test fun malformedUrlsOnlyLoseTheHttpsPromise() {
        val malformed = listOf(
            "",
            "   ",
            "not a url",
            "example.com",
            "https://",
            "https:///path",
            "https://exa mple.com",
            "https://example.com:",
            "https://example.com:port",
            "https://example.com:99999",
            "https://example.com:0",
            "https://example.com:+80",
            "https://[::1",
            "https://[::1]x",
            "https://[nope]/",
            "https://a..b/",
            "https://%65xample.com/",
            "https://good.example\\@evil.example/",
            "://example.com",
        )
        malformed.forEach { url ->
            assertThat(warnings(url)).containsExactly(UrlWarning.NOT_HTTPS)
            assertThat(UrlSafety.isOpenable(url)).isFalse()
        }
    }

    @Test fun openableOnlyForHttpAndHttps() {
        val openable = listOf(
            "http://example.com",
            "https://example.com/path",
            "HTTPS://EXAMPLE.COM",
            "hTTp://example.com:8080",
            "https://127.0.0.1/",
            "https://[::1]/",
            "https://user:pw@example.com",
            "https://example.com/a|b{c}",
            "https://under_score.example.com",
            "https://example.com./",
            "https://हिन्दी.example/",
        )
        openable.forEach { assertThat(UrlSafety.isOpenable(it)).isTrue() }
    }

    @Test fun otherSchemesAreNeverOpenable() {
        val closed = listOf(
            "javascript:alert(1)",
            "javascript://example.com/%0aalert(1)",
            "intent://example.com/#Intent;scheme=https;end",
            "file:///etc/hosts",
            "content://com.example.provider/secret",
            "data:text/html,hello",
            "tel:+15550100",
            "mailto:a@example.com",
            "ftp://example.com",
            "market://details?id=x",
        )
        closed.forEach { assertThat(UrlSafety.isOpenable(it)).isFalse() }
    }

    @Test fun lineBreaksAndControlCharactersAreNotOpenable() {
        assertThat(UrlSafety.isOpenable("https://exa\nmple.com")).isFalse()
        assertThat(UrlSafety.isOpenable("https://example.com/\t")).isFalse()
        assertThat(UrlSafety.isOpenable("https://example.com/\u0000")).isFalse()
        assertThat(UrlSafety.isOpenable(" https://example.com")).isFalse()
    }
}
