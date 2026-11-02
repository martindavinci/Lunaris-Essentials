package io.github.martindavinci.lunaris.scanner.feature.result

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import org.junit.Test

class ResultActionsTest {
    private val raw = "raw text"
    private val copyAndShare = listOf(ResultAction.Copy(raw, sensitive = false), ResultAction.Share(raw))

    private val contact = ParsedContent.Contact("Ada", null, null, listOf("+39 02"), emptyList(), null, null, null)
    private val email = ParsedContent.Email("a@example.com", "Hi", null)
    private val sms = ParsedContent.Sms("+39 02", "Hi")
    private val geo = ParsedContent.Geo(45.5, 9.19, null)
    private val event = ParsedContent.Event("Talk", 1L, 2L, null, null)

    private fun wifi(password: String?) = ParsedContent.Wifi("home", password, WifiSecurity.WPA, hidden = false)

    @Test fun httpsUrl_canBeOpened() {
        val url = "https://example.com"
        assertThat(ResultActions.forContent(ParsedContent.Url(url), raw))
            .isEqualTo(listOf(ResultAction.OpenUrl(url)) + copyAndShare)
    }

    @Test fun httpUrl_canBeOpened() {
        val url = "http://example.com"
        assertThat(ResultActions.forContent(ParsedContent.Url(url), raw)).contains(ResultAction.OpenUrl(url))
    }

    @Test fun otherSchemes_cannotBeOpened() {
        listOf("javascript:alert(1)", "intent://x#Intent;end", "file:///etc/hosts", "ftp://example.com").forEach { url ->
            assertThat(ResultActions.forContent(ParsedContent.Url(url), raw)).isEqualTo(copyAndShare)
        }
    }

    @Test fun wifiWithPassword_offersConnectAndPassword_andCopiesAsSensitive() {
        val content = wifi("secret123")
        assertThat(ResultActions.forContent(content, raw)).isEqualTo(
            listOf(
                ResultAction.ConnectWifi(content),
                ResultAction.CopyPassword("secret123"),
                ResultAction.Copy(raw, sensitive = true),
                ResultAction.Share(raw),
            ),
        )
    }

    @Test fun wifiWithoutPassword_hasNoPasswordAction() {
        listOf(null, "").forEach { password ->
            val content = wifi(password)
            assertThat(ResultActions.forContent(content, raw))
                .isEqualTo(listOf(ResultAction.ConnectWifi(content)) + copyAndShare)
        }
    }

    @Test fun contact_addsToContacts() {
        assertThat(ResultActions.forContent(contact, raw)).isEqualTo(listOf(ResultAction.AddContact(contact)) + copyAndShare)
    }

    @Test fun email_writesEmail() {
        assertThat(ResultActions.forContent(email, raw)).isEqualTo(listOf(ResultAction.SendEmail(email)) + copyAndShare)
    }

    @Test fun phone_opensDialer() {
        assertThat(ResultActions.forContent(ParsedContent.Phone("+39 02"), raw))
            .isEqualTo(listOf(ResultAction.Dial("+39 02")) + copyAndShare)
    }

    @Test fun sms_writesMessage() {
        assertThat(ResultActions.forContent(sms, raw)).isEqualTo(listOf(ResultAction.SendSms(sms)) + copyAndShare)
    }

    @Test fun geo_showsMap() {
        assertThat(ResultActions.forContent(geo, raw)).isEqualTo(listOf(ResultAction.ViewMap(geo)) + copyAndShare)
    }

    @Test fun event_addsToCalendar() {
        assertThat(ResultActions.forContent(event, raw)).isEqualTo(listOf(ResultAction.AddEvent(event)) + copyAndShare)
    }

    @Test fun product_searchesTheCode() {
        assertThat(ResultActions.forContent(ParsedContent.Product("4006381333931", CodeFormat.EAN_13), raw))
            .isEqualTo(listOf(ResultAction.WebSearch("4006381333931")) + copyAndShare)
    }

    @Test fun text_onlyCopiesAndShares() {
        assertThat(ResultActions.forContent(ParsedContent.Text("hello"), raw)).isEqualTo(copyAndShare)
    }
}
