package io.github.martindavinci.lunaris.scanner.feature.scan

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.model.UrlWarning
import org.junit.Test

class CodeCardTest {
    private fun qr(text: String) = codeCard(ScannedCode(text, CodeFormat.QR_CODE))

    @Test fun aCleanLink_opensAtOnce() {
        val card = qr("https://example.com/menu")

        assertThat(card.main).isEqualTo(CardAction.OpenLink("https://example.com/menu"))
        assertThat(card.warnings).isEmpty()
        assertThat(card.summary).isEqualTo("https://example.com/menu")
    }

    @Test fun aLinkWithWarnings_goesThroughTheFullPage_andSaysWhy() {
        val card = qr("http://192.168.4.20/login")

        assertThat(card.main).isEqualTo(CardAction.OpenDetails)
        assertThat(card.warnings).containsAtLeast(UrlWarning.NOT_HTTPS, UrlWarning.IP_ADDRESS_HOST)
    }

    @Test fun aWifiNetwork_goesThroughTheFullPage_andShowsNoPassword() {
        val card = qr("WIFI:T:WPA;S:Ufficio;P:segreta;;")

        assertThat(card.main).isEqualTo(CardAction.OpenDetails)
        assertThat(card.summary).isEqualTo("Ufficio")
        assertThat(card.summary).doesNotContain("segreta")
        assertThat(card.sensitive).isTrue()
    }

    @Test fun aContact_goesThroughTheFullPage() {
        val card = qr("BEGIN:VCARD\nVERSION:3.0\nFN:A. Example\nEND:VCARD")

        assertThat(card.main).isEqualTo(CardAction.OpenDetails)
        assertThat(card.summary).isEqualTo("A. Example")
    }

    @Test fun anEmailAddress_goesThroughTheFullPage() {
        assertThat(qr("mailto:a@example.com").main).isEqualTo(CardAction.OpenDetails)
    }

    @Test fun plainText_hasNothingToOpen() {
        val card = qr("just some words")

        assertThat(card.main).isEqualTo(CardAction.None)
        assertThat(card.sensitive).isFalse()
    }

    @Test fun aProductCode_hasNothingToOpen() {
        val card = codeCard(ScannedCode("8001234567895", CodeFormat.EAN_13))

        assertThat(card.main).isEqualTo(CardAction.None)
        assertThat(card.summary).isEqualTo("8001234567895")
    }

    @Test fun longText_comesOnOneLine() {
        assertThat(qr("one\ntwo   three").summary).isEqualTo("one two three")
    }

    @Test fun copyingAllCodes_givesOnePerLine_newestFirst() {
        val session = listOf(
            SessionCode(ScannedCode("second", CodeFormat.QR_CODE), 2),
            SessionCode(ScannedCode("first", CodeFormat.QR_CODE), 1),
        )

        assertThat(sessionText(session)).isEqualTo("second\nfirst")
        assertThat(sessionIsSensitive(session)).isFalse()
    }

    @Test fun aNetworkAmongTheCodes_makesTheCopySensitive() {
        val session = listOf(
            SessionCode(ScannedCode("words", CodeFormat.QR_CODE), 2),
            SessionCode(ScannedCode("WIFI:T:WPA;S:Ufficio;P:segreta;;", CodeFormat.QR_CODE), 1),
        )

        assertThat(sessionIsSensitive(session)).isTrue()
    }
}
