package io.github.martindavinci.lunaris.scanner.feature.create

import io.github.martindavinci.lunaris.scanner.encode.CodePayloads
import io.github.martindavinci.lunaris.scanner.encode.CodeValidator
import io.github.martindavinci.lunaris.scanner.encode.InvalidReason
import io.github.martindavinci.lunaris.scanner.encode.ValidationResult
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

sealed interface PayloadResult {
    /** A required field is still empty. Not an error: the user is not done typing. */
    data object Incomplete : PayloadResult

    data class Ready(val payload: String, val format: CodeFormat) : PayloadResult

    data class Invalid(val reason: InvalidReason) : PayloadResult
}

/** Turns a [CreateForm] into the text to encode. */
object CreatePayload {
    private const val DEFAULT_URL_SCHEME = "https://"
    private val URL_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    private val EMPTY_CONTACT = ParsedContent.Contact(
        name = null,
        organization = null,
        title = null,
        phones = emptyList(),
        emails = emptyList(),
        address = null,
        website = null,
        note = null,
    )

    fun formatsFor(type: CreateType, encodable: List<CodeFormat>): List<CodeFormat> = when (type) {
        CreateType.TEXT -> encodable.filter { it.is2d }
        CreateType.BARCODE -> encodable.filterNot { it.is2d }
        else -> listOf(CodeFormat.QR_CODE)
    }

    fun evaluate(form: CreateForm): PayloadResult {
        val payload = payloadOf(form) ?: return PayloadResult.Incomplete
        return when (val validation = CodeValidator.validate(payload, form.format)) {
            ValidationResult.Valid -> PayloadResult.Ready(payload, form.format)
            is ValidationResult.Invalid -> PayloadResult.Invalid(validation.reason)
        }
    }

    /** The text to encode, or null while a required field is empty. */
    fun payloadOf(form: CreateForm): String? = when (form.type) {
        CreateType.TEXT -> form.text.ifEmpty { null }
        CreateType.URL -> form.url.trimmedOrNull()?.let(::withScheme)
        CreateType.WIFI -> wifi(form.wifi)
        CreateType.CONTACT -> contact(form.contact)
        CreateType.EMAIL -> email(form.email)
        CreateType.PHONE -> form.phone.trimmedOrNull()?.let { CodePayloads.phone(it) }
        CreateType.SMS -> sms(form.sms)
        CreateType.BARCODE -> form.barcode.trimmedOrNull()
    }

    private fun withScheme(url: String): String =
        if (URL_SCHEME.containsMatchIn(url)) url else DEFAULT_URL_SCHEME + url

    /** Network names and passwords are not trimmed: spaces can be part of the real value. */
    private fun wifi(fields: WifiFields): String? {
        if (fields.ssid.isBlank()) return null
        val open = fields.security == WifiSecurity.NONE
        if (!open && fields.password.isEmpty()) return null
        val password = if (open) null else fields.password
        return CodePayloads.wifi(fields.ssid, password, fields.security, fields.hidden)
    }

    private fun contact(fields: ContactFields): String? {
        val contact = ParsedContent.Contact(
            name = fields.name.trimmedOrNull(),
            organization = fields.organization.trimmedOrNull(),
            title = fields.title.trimmedOrNull(),
            phones = listOfNotNull(fields.phone.trimmedOrNull()),
            emails = listOfNotNull(fields.email.trimmedOrNull()),
            address = fields.address.trimmedOrNull(),
            website = fields.website.trimmedOrNull(),
            note = fields.note.trimmedOrNull(),
        )
        return if (contact == EMPTY_CONTACT) null else CodePayloads.contact(contact)
    }

    private fun email(fields: EmailFields): String? {
        val to = fields.to.trimmedOrNull() ?: return null
        return CodePayloads.email(to, fields.subject.trimmedOrNull(), fields.body.ifBlank { null })
    }

    private fun sms(fields: SmsFields): String? {
        val number = fields.number.trimmedOrNull() ?: return null
        return CodePayloads.sms(number, fields.body.ifBlank { null })
    }

    private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }
}
