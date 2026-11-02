package io.github.martindavinci.lunaris.scanner.feature.result

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import org.junit.Test

class ResultFieldsTest {
    @Test fun url_showsTheWholeUrl() {
        val url = "https://example.com/a/very/long/path?with=query#and-fragment"
        assertThat(ResultFields.forContent(ParsedContent.Url(url)))
            .containsExactly(ResultField.Plain(FieldLabel.URL, url))
    }

    @Test fun wifi_marksThePasswordAsSecret() {
        val content = ParsedContent.Wifi("home", "secret123", WifiSecurity.WPA, hidden = true)
        assertThat(ResultFields.forContent(content)).containsExactly(
            ResultField.Plain(FieldLabel.SSID, "home"),
            ResultField.Secret(FieldLabel.PASSWORD, "secret123"),
            ResultField.Security(WifiSecurity.WPA),
            ResultField.Flag(FieldLabel.HIDDEN_NETWORK),
        ).inOrder()
    }

    @Test fun openWifi_hasNoPasswordField() {
        val content = ParsedContent.Wifi("cafe", "", WifiSecurity.NONE, hidden = false)
        assertThat(ResultFields.forContent(content)).containsExactly(
            ResultField.Plain(FieldLabel.SSID, "cafe"),
            ResultField.Security(WifiSecurity.NONE),
        ).inOrder()
    }

    @Test fun contact_listsEveryPhoneAndEmail_andSkipsBlanks() {
        val content = ParsedContent.Contact(
            name = "Ada",
            organization = " ",
            title = null,
            phones = listOf("1", "2"),
            emails = listOf("a@example.com"),
            address = null,
            website = "https://example.com",
            note = "",
        )
        assertThat(ResultFields.forContent(content)).containsExactly(
            ResultField.Plain(FieldLabel.NAME, "Ada"),
            ResultField.Plain(FieldLabel.PHONE, "1"),
            ResultField.Plain(FieldLabel.PHONE, "2"),
            ResultField.Plain(FieldLabel.EMAIL, "a@example.com"),
            ResultField.Plain(FieldLabel.WEBSITE, "https://example.com"),
        ).inOrder()
    }

    @Test fun email_phone_sms() {
        assertThat(ResultFields.forContent(ParsedContent.Email("a@example.com", "Hi", null))).containsExactly(
            ResultField.Plain(FieldLabel.TO, "a@example.com"),
            ResultField.Plain(FieldLabel.SUBJECT, "Hi"),
        ).inOrder()
        assertThat(ResultFields.forContent(ParsedContent.Phone("123")))
            .containsExactly(ResultField.Plain(FieldLabel.NUMBER, "123"))
        assertThat(ResultFields.forContent(ParsedContent.Sms("123", "Hi"))).containsExactly(
            ResultField.Plain(FieldLabel.NUMBER, "123"),
            ResultField.Plain(FieldLabel.MESSAGE, "Hi"),
        ).inOrder()
    }

    @Test fun geo_showsCoordinatesAndQuery() {
        assertThat(ResultFields.forContent(ParsedContent.Geo(45.5, 9.19, "Milan"))).containsExactly(
            ResultField.Plain(FieldLabel.COORDINATES, "45.5,9.19"),
            ResultField.Plain(FieldLabel.QUERY, "Milan"),
        ).inOrder()
    }

    @Test fun event_keepsTimesAsMillis() {
        val content = ParsedContent.Event("Talk", 1L, null, "Room 1", null)
        assertThat(ResultFields.forContent(content)).containsExactly(
            ResultField.Plain(FieldLabel.SUMMARY, "Talk"),
            ResultField.Time(FieldLabel.START, 1L),
            ResultField.Plain(FieldLabel.LOCATION, "Room 1"),
        ).inOrder()
    }

    @Test fun product_showsTheCode() {
        assertThat(ResultFields.forContent(ParsedContent.Product("123", CodeFormat.EAN_8)))
            .containsExactly(ResultField.Plain(FieldLabel.CODE, "123"))
    }

    @Test fun text_hasNoFields_theRawTextIsEnough() {
        assertThat(ResultFields.forContent(ParsedContent.Text("hello"))).isEmpty()
    }

    @Test fun coordinates_neverUseScientificNotation() {
        val text = coordinates(ParsedContent.Geo(0.00001, -120.5, null))
        assertThat(text).doesNotContain("E")
        assertThat(text).endsWith(",-120.5")
    }
}
