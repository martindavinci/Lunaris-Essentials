package io.github.martindavinci.lunaris.scanner.feature.create

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.encode.CodePayloads
import io.github.martindavinci.lunaris.scanner.encode.InvalidReason
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity
import org.junit.Test

class CreatePayloadTest {
    private fun payload(form: CreateForm) = CreatePayload.payloadOf(form)

    @Test fun text_isKeptAsTyped() {
        assertThat(payload(CreateForm(text = "  hello  "))).isEqualTo("  hello  ")
        assertThat(payload(CreateForm(text = ""))).isNull()
    }

    @Test fun url_getsHttpsOnlyWhenItHasNoScheme() {
        fun url(value: String) = payload(CreateForm(type = CreateType.URL, url = value))

        assertThat(url(" example.com/a ")).isEqualTo("https://example.com/a")
        assertThat(url("example.com:8080")).isEqualTo("https://example.com:8080")
        assertThat(url("http://example.com")).isEqualTo("http://example.com")
        assertThat(url("  ")).isNull()
    }

    @Test fun wifi_needsANetworkName() {
        val form = CreateForm(type = CreateType.WIFI, wifi = WifiFields(ssid = " ", password = "secret"))
        assertThat(payload(form)).isNull()
    }

    @Test fun wifi_withSecurity_needsAPassword() {
        val fields = WifiFields(ssid = "Home", security = WifiSecurity.WPA)
        assertThat(payload(CreateForm(type = CreateType.WIFI, wifi = fields))).isNull()

        val complete = fields.copy(password = "secret", hidden = true)
        assertThat(payload(CreateForm(type = CreateType.WIFI, wifi = complete)))
            .isEqualTo(CodePayloads.wifi("Home", "secret", WifiSecurity.WPA, true))
    }

    @Test fun wifi_open_dropsAPasswordTypedBefore() {
        val fields = WifiFields(ssid = "Cafe", password = "old", security = WifiSecurity.NONE)
        assertThat(payload(CreateForm(type = CreateType.WIFI, wifi = fields)))
            .isEqualTo(CodePayloads.wifi("Cafe", null, WifiSecurity.NONE, false))
    }

    @Test fun contact_blankIsIncomplete_andFieldsAreTrimmed() {
        assertThat(payload(CreateForm(type = CreateType.CONTACT))).isNull()
        assertThat(payload(CreateForm(type = CreateType.CONTACT, contact = ContactFields(name = "  ")))).isNull()

        val fields = ContactFields(name = " Ada ", phone = " +39 1 ", email = "ada@example.com")
        val expected = ParsedContent.Contact(
            name = "Ada",
            organization = null,
            title = null,
            phones = listOf("+39 1"),
            emails = listOf("ada@example.com"),
            address = null,
            website = null,
            note = null,
        )
        assertThat(payload(CreateForm(type = CreateType.CONTACT, contact = fields)))
            .isEqualTo(CodePayloads.contact(expected))
    }

    @Test fun email_needsARecipient_andBlankPartsAreLeftOut() {
        assertThat(payload(CreateForm(type = CreateType.EMAIL, email = EmailFields(subject = "Hi")))).isNull()

        val fields = EmailFields(to = " a@example.com ", subject = " ", body = "Hello")
        assertThat(payload(CreateForm(type = CreateType.EMAIL, email = fields)))
            .isEqualTo(CodePayloads.email("a@example.com", null, "Hello"))
    }

    @Test fun phone_isTrimmed() {
        assertThat(payload(CreateForm(type = CreateType.PHONE, phone = " +391 ")))
            .isEqualTo(CodePayloads.phone("+391"))
        assertThat(payload(CreateForm(type = CreateType.PHONE, phone = " "))).isNull()
    }

    @Test fun sms_needsANumber_andBodyIsOptional() {
        assertThat(payload(CreateForm(type = CreateType.SMS, sms = SmsFields(body = "Hi")))).isNull()
        assertThat(payload(CreateForm(type = CreateType.SMS, sms = SmsFields(number = "123", body = " "))))
            .isEqualTo(CodePayloads.sms("123", null))
        assertThat(payload(CreateForm(type = CreateType.SMS, sms = SmsFields(number = "123", body = "Hi"))))
            .isEqualTo(CodePayloads.sms("123", "Hi"))
    }

    @Test fun barcode_isTrimmed() {
        assertThat(payload(CreateForm(type = CreateType.BARCODE, barcode = " 123 "))).isEqualTo("123")
    }

    @Test fun formats_textIs2d_barcodeIs1d_restIsLockedToQr() {
        val encodable = listOf(CodeFormat.QR_CODE, CodeFormat.AZTEC, CodeFormat.EAN_13, CodeFormat.CODE_128)

        assertThat(CreatePayload.formatsFor(CreateType.TEXT, encodable))
            .containsExactly(CodeFormat.QR_CODE, CodeFormat.AZTEC).inOrder()
        assertThat(CreatePayload.formatsFor(CreateType.BARCODE, encodable))
            .containsExactly(CodeFormat.EAN_13, CodeFormat.CODE_128).inOrder()
        val structured = CreateType.entries - CreateType.TEXT - CreateType.BARCODE
        structured.forEach { type ->
            assertThat(CreatePayload.formatsFor(type, encodable)).containsExactly(CodeFormat.QR_CODE)
        }
    }

    @Test fun form_keepsAFormatPerType_andStructuredTypesStayOnQr() {
        val form = CreateForm(type = CreateType.BARCODE).withFormat(CodeFormat.EAN_8)
        assertThat(form.format).isEqualTo(CodeFormat.EAN_8)
        assertThat(form.copy(type = CreateType.TEXT).format).isEqualTo(CodeFormat.QR_CODE)

        val url = form.copy(type = CreateType.URL)
        assertThat(url.withFormat(CodeFormat.AZTEC).format).isEqualTo(CodeFormat.QR_CODE)
    }

    @Test fun evaluate_incompleteIsNotAnError() {
        assertThat(CreatePayload.evaluate(CreateForm())).isEqualTo(PayloadResult.Incomplete)
    }

    @Test fun evaluate_valid_isReadyWithItsFormat() {
        assertThat(CreatePayload.evaluate(CreateForm(text = "hello")))
            .isEqualTo(PayloadResult.Ready("hello", CodeFormat.QR_CODE))

        val barcode = CreateForm(type = CreateType.BARCODE, barcode = "1234567890128", barcodeFormat = CodeFormat.EAN_13)
        assertThat(CreatePayload.evaluate(barcode))
            .isEqualTo(PayloadResult.Ready("1234567890128", CodeFormat.EAN_13))
    }

    @Test fun evaluate_reportsWhatTheValidatorRejects() {
        val barcode = CreateForm(type = CreateType.BARCODE, barcode = "1234567890123", barcodeFormat = CodeFormat.EAN_13)
        assertThat(CreatePayload.evaluate(barcode)).isEqualTo(PayloadResult.Invalid(InvalidReason.BAD_CHECK_DIGIT))
    }

    @Test fun onlyAWifiCodeWithAPasswordHoldsOne() {
        val wifi = WifiFields(ssid = "Home", password = "secret", security = WifiSecurity.WPA)

        assertThat(CreateForm(type = CreateType.WIFI, wifi = wifi).holdsPassword).isTrue()
        assertThat(CreateForm(type = CreateType.WIFI, wifi = wifi.copy(password = "")).holdsPassword).isFalse()
        assertThat(CreateForm(type = CreateType.WIFI, wifi = wifi.copy(security = WifiSecurity.NONE)).holdsPassword)
            .isFalse()
        assertThat(CreateForm(type = CreateType.TEXT, wifi = wifi).holdsPassword).isFalse()
    }
}
