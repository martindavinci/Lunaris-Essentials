package io.github.martindavinci.lunaris.scanner.feature.history

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.history.HistoryEntry
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import io.github.martindavinci.lunaris.scanner.parse.ContentParser
import org.junit.Test

class HistoryItemTest {
    private val wifiText = "WIFI:T:WPA;S:Home;P:s3cret;;"
    private val wifi = ParsedContent.Wifi("Home", "s3cret", WifiSecurity.WPA, hidden = false)

    private fun itemOf(content: ParsedContent, text: String = "raw") =
        HistoryEntry(1, text, CodeFormat.QR_CODE, 100).toItem(content)

    private fun contact(
        name: String? = null,
        organization: String? = null,
        phones: List<String> = emptyList(),
        emails: List<String> = emptyList(),
    ) = ParsedContent.Contact(name, organization, null, phones, emails, null, null, null)

    @Test fun keepsTheCodeItCameFrom() {
        val item = itemOf(ParsedContent.Text("raw"))

        assertThat(item.id).isEqualTo(1)
        assertThat(item.code).isEqualTo(ScannedCode("raw", CodeFormat.QR_CODE))
        assertThat(item.timestampMillis).isEqualTo(100)
    }

    @Test fun wifi_showsTheSsidAndNeverThePassword() {
        val item = itemOf(wifi, wifiText)

        assertThat(item.kind).isEqualTo(HistoryKind.WIFI)
        assertThat(item.summary).isEqualTo("Home")
    }

    @Test fun wifi_withoutAnSsid_showsNothingRatherThanThePassword() {
        assertThat(itemOf(wifi.copy(ssid = ""), wifiText).summary).isEmpty()
    }

    @Test fun wifi_isNotFoundByItsPassword() {
        val items = listOf(itemOf(wifi, wifiText))

        assertThat(items.matching("s3cret")).isEmpty()
        assertThat(items.matching("WPA")).isEmpty()
        assertThat(items.matching("home")).isEqualTo(items)
    }

    @Test fun url_showsTheUrl() {
        val item = itemOf(ParsedContent.Url("https://example.com"))

        assertThat(item.kind).isEqualTo(HistoryKind.URL)
        assertThat(item.summary).isEqualTo("https://example.com")
    }

    @Test fun contact_showsTheFirstThingKnown() {
        assertThat(itemOf(contact(name = "Ada", organization = "Org")).summary).isEqualTo("Ada")
        assertThat(itemOf(contact(name = " ", organization = "Org")).summary).isEqualTo("Org")
        assertThat(itemOf(contact(phones = listOf("+39 1"))).summary).isEqualTo("+39 1")
        assertThat(itemOf(contact(emails = listOf("a@b.c"))).summary).isEqualTo("a@b.c")
        assertThat(itemOf(contact()).summary).isEmpty()
        assertThat(itemOf(contact()).kind).isEqualTo(HistoryKind.CONTACT)
    }

    @Test fun email_phoneAndSms_showTheAddress() {
        val email = itemOf(ParsedContent.Email("a@b.c", "subject", "body"))
        val phone = itemOf(ParsedContent.Phone("123"))
        val sms = itemOf(ParsedContent.Sms("456", "body"))

        assertThat(email.kind).isEqualTo(HistoryKind.EMAIL)
        assertThat(email.summary).isEqualTo("a@b.c")
        assertThat(phone.kind).isEqualTo(HistoryKind.PHONE)
        assertThat(phone.summary).isEqualTo("123")
        assertThat(sms.kind).isEqualTo(HistoryKind.SMS)
        assertThat(sms.summary).isEqualTo("456")
    }

    @Test fun geo_showsTheQueryOrTheCoordinates() {
        assertThat(itemOf(ParsedContent.Geo(1.5, 2.5, "Vinci")).summary).isEqualTo("Vinci")
        assertThat(itemOf(ParsedContent.Geo(1.5, 2.5, null)).summary).isEqualTo("1.5, 2.5")
        assertThat(itemOf(ParsedContent.Geo(1.5, 2.5, "")).summary).isEqualTo("1.5, 2.5")
        assertThat(itemOf(ParsedContent.Geo(1.5, 2.5, null)).kind).isEqualTo(HistoryKind.GEO)
    }

    @Test fun event_showsTheSummaryOrTheLocation() {
        val titled = itemOf(ParsedContent.Event("Launch", null, null, "Milan", null))
        val untitled = itemOf(ParsedContent.Event(null, null, null, "Milan", null))

        assertThat(titled.kind).isEqualTo(HistoryKind.EVENT)
        assertThat(titled.summary).isEqualTo("Launch")
        assertThat(untitled.summary).isEqualTo("Milan")
    }

    @Test fun product_showsTheCode() {
        val item = itemOf(ParsedContent.Product("4006381333931", CodeFormat.EAN_13))

        assertThat(item.kind).isEqualTo(HistoryKind.PRODUCT)
        assertThat(item.summary).isEqualTo("4006381333931")
    }

    @Test fun text_isFlattenedAndShortened() {
        val item = itemOf(ParsedContent.Text("  one\n\ntwo\tthree  " + "x".repeat(1_000)))

        assertThat(item.kind).isEqualTo(HistoryKind.TEXT)
        assertThat(item.summary).startsWith("one two three")
        assertThat(item.summary.length).isLessThan(1_000)
    }

    @Test fun matching_blankQuery_keepsEverything() {
        val items = listOf(itemOf(ParsedContent.Text("a")), itemOf(ParsedContent.Text("b")))

        assertThat(items.matching("")).isEqualTo(items)
        assertThat(items.matching("   ")).isEqualTo(items)
    }

    @Test fun matching_ignoresCaseAndOuterSpaces() {
        val match = itemOf(ParsedContent.Text("Hello World"), "Hello World")
        val other = itemOf(ParsedContent.Text("Other"), "Other")

        assertThat(listOf(match, other).matching("  hello w ")).containsExactly(match)
    }

    @Test fun matching_looksAtTheWholeContentNotOnlyTheSummary() {
        val vcard = "BEGIN:VCARD\nFN:Ada\nTEL:+39123\nEND:VCARD"
        val item = itemOf(contact(name = "Ada", phones = listOf("+39123")), vcard)

        assertThat(listOf(item).matching("+39123")).containsExactly(item)
    }

    @Test fun wifiCodeWithoutAnSsid_doesNotLeakItsPassword() {
        val text = "WIFI:T:WPA;P:s3cret;;"
        val item = HistoryEntry(1, text, CodeFormat.QR_CODE, 100).toItem(ContentParser.parse(ScannedCode(text, CodeFormat.QR_CODE)))

        assertThat(item.kind).isEqualTo(HistoryKind.WIFI)
        assertThat(item.summary).isEmpty()
        assertThat(listOf(item).matching("s3cret")).isEmpty()
    }

    @Test fun summary_spellsOutControlsThatReorderText() {
        assertThat(itemOf(ParsedContent.Text("a\u202Eb")).summary).isEqualTo("a\\u202Eb")
    }
}
