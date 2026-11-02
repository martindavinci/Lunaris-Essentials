package io.github.martindavinci.lunaris.scanner.encode

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.google.zxing.client.result.AddressBookParsedResult
import com.google.zxing.client.result.EmailAddressParsedResult
import com.google.zxing.client.result.GeoParsedResult
import com.google.zxing.client.result.ParsedResult
import com.google.zxing.client.result.ResultParser
import com.google.zxing.client.result.SMSParsedResult
import com.google.zxing.client.result.TelParsedResult
import com.google.zxing.client.result.WifiParsedResult
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import org.junit.Test

class CodePayloadsTest {
    private fun parsed(payload: String): ParsedResult =
        ResultParser.parseResult(Result(payload, null, null, BarcodeFormat.QR_CODE))

    @Test
    fun wifiWritesTheStandardFields() {
        assertThat(CodePayloads.wifi("Home", "secret123", WifiSecurity.WPA, hidden = false))
            .isEqualTo("WIFI:T:WPA;S:Home;P:secret123;;")
        assertThat(CodePayloads.wifi("Home", "abcde", WifiSecurity.WEP, hidden = false))
            .isEqualTo("WIFI:T:WEP;S:Home;P:abcde;;")
    }

    @Test
    fun wifiWithoutSecurityLeavesThePasswordOut() {
        assertThat(CodePayloads.wifi("Cafe", "ignored", WifiSecurity.NONE, hidden = false))
            .isEqualTo("WIFI:T:nopass;S:Cafe;;")
        assertThat(CodePayloads.wifi("Cafe", null, WifiSecurity.NONE, hidden = false))
            .isEqualTo("WIFI:T:nopass;S:Cafe;;")
    }

    @Test
    fun wifiMarksHiddenNetworks() {
        assertThat(CodePayloads.wifi("Home", "secret123", WifiSecurity.WPA, hidden = true))
            .isEqualTo("WIFI:T:WPA;S:Home;P:secret123;H:true;;")
    }

    @Test
    fun wifiEscapesTheSpecialCharacters() {
        assertThat(CodePayloads.wifi("""a;b,c:d"e\f""", """p;w,d:"\""", WifiSecurity.WPA, false))
            .isEqualTo("""WIFI:T:WPA;S:a\;b\,c\:d\"e\\f;P:p\;w\,d\:\"\\;;""")
    }

    @Test
    fun wifiEscapingSurvivesAnIndependentParser() {
        val ssid = """a;b,c:d"e\f"""
        val password = """p;w,d:"\x"""
        val result = parsed(CodePayloads.wifi(ssid, password, WifiSecurity.WPA, true)) as WifiParsedResult
        assertThat(result.ssid).isEqualTo(ssid)
        assertThat(result.password).isEqualTo(password)
        assertThat(result.networkEncryption).isEqualTo("WPA")
        assertThat(result.isHidden).isTrue()
    }

    @Test
    fun emailPercentEncodesEveryPart() {
        assertThat(CodePayloads.email("a@example.com", "Hi there & more", "Line 1\nCaffè?"))
            .isEqualTo("mailto:a@example.com?subject=Hi%20there%20%26%20more&body=Line%201%0D%0ACaff%C3%A8%3F")
    }

    @Test
    fun emailLeavesOutMissingParts() {
        assertThat(CodePayloads.email("a@example.com", null, null)).isEqualTo("mailto:a@example.com")
        assertThat(CodePayloads.email("a@example.com", "", "Hello"))
            .isEqualTo("mailto:a@example.com?body=Hello")
    }

    @Test
    fun emailSurvivesAnIndependentParser() {
        val payload = CodePayloads.email("me+tag@example.com", "A+B = C", "Caffè & cornetto\nCiao")
        val result = parsed(payload) as EmailAddressParsedResult
        assertThat(result.tos).asList().containsExactly("me+tag@example.com")
        assertThat(result.subject).isEqualTo("A+B = C")
        assertThat(result.body).isEqualTo("Caffè & cornetto\r\nCiao")
    }

    @Test
    fun phoneDropsVisualSeparators() {
        assertThat(CodePayloads.phone(" +39 (02) 123-45.67 ")).isEqualTo("tel:+39021234567")
        assertThat((parsed(CodePayloads.phone("+39 02 1234567")) as TelParsedResult).number)
            .isEqualTo("+39021234567")
    }

    @Test
    fun phoneEscapesTheHashSoItIsNotAFragment() {
        assertThat(CodePayloads.phone("*21#")).isEqualTo("tel:*21%23")
    }

    @Test
    fun smsKeepsColonsInTheBody() {
        assertThat(CodePayloads.sms("+39 333 1234567", "Ore 10:30, ok?"))
            .isEqualTo("SMSTO:+393331234567:Ore 10:30, ok?")
        val result = parsed(CodePayloads.sms("+393331234567", "Ore 10:30")) as SMSParsedResult
        assertThat(result.numbers).asList().containsExactly("+393331234567")
        assertThat(result.body).isEqualTo("Ore 10:30")
    }

    @Test
    fun smsWithoutBodyHasOnlyTheNumber() {
        assertThat(CodePayloads.sms("112", null)).isEqualTo("SMSTO:112")
        assertThat(CodePayloads.sms("112", "")).isEqualTo("SMSTO:112")
    }

    @Test
    fun geoAvoidsExponentsAndTrailingZeros() {
        assertThat(CodePayloads.geo(45.4642, 9.19)).isEqualTo("geo:45.4642,9.19")
        assertThat(CodePayloads.geo(0.0001, -0.00002)).isEqualTo("geo:0.0001,-0.00002")
        assertThat(CodePayloads.geo(100.0, 0.0)).isEqualTo("geo:100,0")
        val result = parsed(CodePayloads.geo(-33.8688, 151.2093)) as GeoParsedResult
        assertThat(result.latitude).isEqualTo(-33.8688)
        assertThat(result.longitude).isEqualTo(151.2093)
    }

    private val fullContact = ParsedContent.Contact(
        name = "Martin DaVinci",
        organization = "Lunaris; Lab",
        title = "Maker, of things",
        phones = listOf("+39 02 1234567", "+39 333 1234567"),
        emails = listOf("a@example.com", "b@example.com"),
        address = "Via Roma 1, Milano",
        website = "https://example.com/a;b,c",
        note = "First line\nSecond \\ line",
    )

    @Test
    fun contactWritesAVCard30WithCrlfLines() {
        assertThat(CodePayloads.contact(fullContact)).isEqualTo(
            listOf(
                "BEGIN:VCARD",
                "VERSION:3.0",
                "N:DaVinci;Martin;;;",
                "FN:Martin DaVinci",
                "ORG:Lunaris\\; Lab",
                "TITLE:Maker\\, of things",
                "TEL:+39 02 1234567",
                "TEL:+39 333 1234567",
                "EMAIL:a@example.com",
                "EMAIL:b@example.com",
                "ADR:;;Via Roma 1\\, Milano;;;;",
                "URL:https://example.com/a;b,c",
                "NOTE:First line\\nSecond \\\\ line",
                "END:VCARD",
                "",
            ).joinToString("\r\n"),
        )
    }

    @Test
    fun contactKeepsTheMandatoryNameFieldsWhenEmpty() {
        val empty = ParsedContent.Contact(null, null, null, emptyList(), listOf("a@example.com"), " ", null, "")
        assertThat(CodePayloads.contact(empty)).isEqualTo(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:;;;;\r\nFN:\r\nEMAIL:a@example.com\r\nEND:VCARD\r\n",
        )
    }

    @Test
    fun contactWithASingleNameUsesItAsTheFamilyName() {
        val single = ParsedContent.Contact("Cher", null, null, emptyList(), emptyList(), null, null, null)
        assertThat(CodePayloads.contact(single)).contains("\r\nN:Cher;;;;\r\nFN:Cher\r\n")
    }

    @Test
    fun contactCannotBreakOutOfAUrlLine() {
        val hostile = fullContact.copy(website = "https://example.com\r\nNOTE:injected")
        assertThat(CodePayloads.contact(hostile)).contains("\r\nURL:https://example.comNOTE:injected\r\n")
    }

    @Test
    fun contactSurvivesAnIndependentParser() {
        val result = parsed(CodePayloads.contact(fullContact)) as AddressBookParsedResult
        assertThat(result.names).asList().containsExactly("Martin DaVinci")
        assertThat(result.org).isEqualTo("Lunaris; Lab")
        assertThat(result.title).isEqualTo("Maker, of things")
        assertThat(result.phoneNumbers).asList().containsExactly("+39 02 1234567", "+39 333 1234567").inOrder()
        assertThat(result.emails).asList().containsExactly("a@example.com", "b@example.com").inOrder()
        assertThat(result.addresses.single()).isEqualTo("Via Roma 1, Milano")
        assertThat(result.urLs).asList().containsExactly("https://example.com/a;b,c")
        assertThat(result.note).isEqualTo("First line\nSecond \\ line")
    }
}
