package io.github.martindavinci.lunaris.scanner.feature.create

import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

enum class CreateType { TEXT, URL, WIFI, CONTACT, EMAIL, PHONE, SMS, BARCODE }

data class WifiFields(
    val ssid: String = "",
    val password: String = "",
    val security: WifiSecurity = WifiSecurity.WPA,
    val hidden: Boolean = false,
)

data class ContactFields(
    val name: String = "",
    val organization: String = "",
    val title: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val website: String = "",
    val note: String = "",
)

data class EmailFields(val to: String = "", val subject: String = "", val body: String = "")

data class SmsFields(val number: String = "", val body: String = "")

/** What the user typed. Every type keeps its own fields, so switching type loses nothing. */
data class CreateForm(
    val type: CreateType = CreateType.TEXT,
    val text: String = "",
    val url: String = "",
    val wifi: WifiFields = WifiFields(),
    val contact: ContactFields = ContactFields(),
    val email: EmailFields = EmailFields(),
    val phone: String = "",
    val sms: SmsFields = SmsFields(),
    val barcode: String = "",
    val textFormat: CodeFormat = CodeFormat.QR_CODE,
    val barcodeFormat: CodeFormat = CodeFormat.CODE_128,
) {
    /** The text of a Wi-Fi code spells out the password, so it counts as secret. */
    val holdsPassword: Boolean
        get() = type == CreateType.WIFI && wifi.security != WifiSecurity.NONE && wifi.password.isNotEmpty()

    /** Only free text and barcodes have a choice: structured content is always a QR code. */
    val format: CodeFormat
        get() = when (type) {
            CreateType.TEXT -> textFormat
            CreateType.BARCODE -> barcodeFormat
            else -> CodeFormat.QR_CODE
        }

    fun withFormat(format: CodeFormat): CreateForm = when (type) {
        CreateType.TEXT -> copy(textFormat = format)
        CreateType.BARCODE -> copy(barcodeFormat = format)
        else -> this
    }
}
