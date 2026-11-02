package io.github.martindavinci.lunaris.scanner.parse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Text
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Url
import org.junit.Test

class UrlContentTest {
    @Test fun httpAndHttps() {
        assertThat(parse("https://example.com")).isEqualTo(Url("https://example.com"))
        assertThat(parse("http://example.com/a?b=c#d")).isEqualTo(Url("http://example.com/a?b=c#d"))
        assertThat(parse("https://example.com:8443/x")).isEqualTo(Url("https://example.com:8443/x"))
    }

    @Test fun schemeIsCaseInsensitiveAndComesOutLowercase() {
        assertThat(parse("HTTPS://EXAMPLE.COM/PATH")).isEqualTo(Url("https://EXAMPLE.COM/PATH"))
        assertThat(parse("hTTp://Example.com")).isEqualTo(Url("http://Example.com"))
    }

    @Test fun surroundingWhitespaceIsIgnored() {
        assertThat(parse("  https://example.com\n")).isEqualTo(Url("https://example.com"))
    }

    @Test fun bareDomainBecomesHttps() {
        assertThat(parse("example.com")).isEqualTo(Url("https://example.com"))
        assertThat(parse("example.com/path")).isEqualTo(Url("https://example.com/path"))
        assertThat(parse("www.example.co.uk?x=1")).isEqualTo(Url("https://www.example.co.uk?x=1"))
        assertThat(parse("example.com#top")).isEqualTo(Url("https://example.com#top"))
        assertThat(parse("my-site.example.org:8080/a"))
            .isEqualTo(Url("https://my-site.example.org:8080/a"))
        assertThat(parse("EXAMPLE.COM/PATH")).isEqualTo(Url("https://EXAMPLE.COM/PATH"))
    }

    @Test fun whatOnlyLooksLikeABareDomainStaysText() {
        val notDomains = listOf(
            "hello",
            "hello world",
            "hello. world",
            "v1.2.3",
            "192.168.1.1",
            "localhost",
            "a@example.com",
            "user:pw@example.com",
            "example.com:80@evil.example",
            "example.com:port",
            "example.com:",
            "example.com/a b",
            "example..com",
            ".example.com",
            "example.com.",
            "example.c",
            "example.c0m",
            "bücher.example",
            "line one\nexample.com",
        )
        notDomains.forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
    }

    @Test fun otherSchemesAreTextNeverUrl() {
        val others = listOf(
            "javascript:alert(1)",
            "JAVASCRIPT:alert(1)",
            "javascript://example.com/%0aalert(1)",
            "javascript:example.com",
            "intent://example.com/#Intent;scheme=https;end",
            "intent:example.com",
            "file:///sdcard/secret.txt",
            "file://example.com/x",
            "content://com.example.provider/secret",
            "data:text/html,<script>alert(1)</script>",
            "data:example.com",
            "ftp://example.com/file",
            "market://details?id=com.example",
            "foo.bar://example.com",
            "vbscript:msgbox(1)",
        )
        others.forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
    }

    @Test fun malformedHttpUrlsAreText() {
        val malformed = listOf(
            "https://",
            "https:///path",
            "http://?x=1",
            "https:example.com",
            "https://exa mple.com",
            "https://example.com/a b",
            "https://example.com:99999",
            "https://example.com:abc",
            "https://[::1",
            "https://good.example\\@evil.example/",
            "https://good.example\\evil",
        )
        malformed.forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
    }

    @Test fun deceptiveButWellFormedUrlsStayUrlsSoTheWarningsCanShow() {
        val url = "https://bank.example.com@evil.example/"
        assertThat(parse(url)).isEqualTo(Url(url))
        assertThat(parse("http://127.0.0.1:8080/")).isEqualTo(Url("http://127.0.0.1:8080/"))
        assertThat(parse("https://аpple.com")).isEqualTo(Url("https://аpple.com"))
    }

    @Test fun textKeepsTheOriginalSpacing() {
        assertThat(parse("  hello \n")).isEqualTo(Text("  hello \n"))
        assertThat(parse("")).isEqualTo(Text(""))
        assertThat(parse("   ")).isEqualTo(Text("   "))
    }

    @Test fun invisibleFormatCharactersMakeItText() {
        val spoof = "https://\u202Ex@evil.example/nigol/moc.knab.www"
        listOf(
            spoof,
            "https://evil.example/\u202Ex",
            "https://evil.example/?a=\u2066b",
            "https://evil.example/\u200Bx",
            "https://evil.example/\uFEFFx",
            "evil.example/\u202Ex",
        ).forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
        assertThat(UrlSafety.isOpenable(spoof)).isFalse()
    }
}
