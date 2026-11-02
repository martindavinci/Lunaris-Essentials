package io.github.martindavinci.lunaris.scanner.feature.result

import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

enum class FieldLabel {
    URL, SSID, PASSWORD, SECURITY, HIDDEN_NETWORK,
    NAME, ORGANIZATION, JOB_TITLE, PHONE, EMAIL, ADDRESS, WEBSITE, NOTE,
    TO, SUBJECT, BODY, NUMBER, MESSAGE,
    COORDINATES, QUERY,
    SUMMARY, START, END, LOCATION, DESCRIPTION,
    CODE,
}

/** One labelled value of a parsed code. Texts and date formats are left to the UI. */
sealed interface ResultField {
    val label: FieldLabel

    data class Plain(override val label: FieldLabel, val value: String) : ResultField
    data class Secret(override val label: FieldLabel, val value: String) : ResultField
    data class Time(override val label: FieldLabel, val millis: Long) : ResultField
    data class Security(val security: WifiSecurity) : ResultField {
        override val label = FieldLabel.SECURITY
    }

    /** A yes/no property that is only listed when it holds. */
    data class Flag(override val label: FieldLabel) : ResultField
}

object ResultFields {
    fun forContent(content: ParsedContent): List<ResultField> = when (content) {
        is ParsedContent.Url -> listOf(ResultField.Plain(FieldLabel.URL, content.url))
        is ParsedContent.Wifi -> wifi(content)
        is ParsedContent.Contact -> contact(content)
        is ParsedContent.Email -> plain(
            FieldLabel.TO to content.to,
            FieldLabel.SUBJECT to content.subject,
            FieldLabel.BODY to content.body,
        )
        is ParsedContent.Phone -> plain(FieldLabel.NUMBER to content.number)
        is ParsedContent.Sms -> plain(FieldLabel.NUMBER to content.number, FieldLabel.MESSAGE to content.body)
        is ParsedContent.Geo -> plain(FieldLabel.COORDINATES to coordinates(content), FieldLabel.QUERY to content.query)
        is ParsedContent.Event -> event(content)
        is ParsedContent.Product -> plain(FieldLabel.CODE to content.code)
        is ParsedContent.Text -> emptyList()
    }

    private fun wifi(wifi: ParsedContent.Wifi): List<ResultField> = listOfNotNull(
        ResultField.Plain(FieldLabel.SSID, wifi.ssid).takeIf { wifi.ssid.isNotEmpty() },
        wifi.secret?.let { ResultField.Secret(FieldLabel.PASSWORD, it) },
        ResultField.Security(wifi.security),
        ResultField.Flag(FieldLabel.HIDDEN_NETWORK).takeIf { wifi.hidden },
    )

    private fun contact(contact: ParsedContent.Contact): List<ResultField> =
        plain(
            FieldLabel.NAME to contact.name,
            FieldLabel.ORGANIZATION to contact.organization,
            FieldLabel.JOB_TITLE to contact.title,
        ) +
            plain(contact.phones.map { FieldLabel.PHONE to it }) +
            plain(contact.emails.map { FieldLabel.EMAIL to it }) +
            plain(
                FieldLabel.ADDRESS to contact.address,
                FieldLabel.WEBSITE to contact.website,
                FieldLabel.NOTE to contact.note,
            )

    private fun event(event: ParsedContent.Event): List<ResultField> =
        plain(FieldLabel.SUMMARY to event.summary) +
            listOfNotNull(
                event.startMillis?.let { ResultField.Time(FieldLabel.START, it) },
                event.endMillis?.let { ResultField.Time(FieldLabel.END, it) },
            ) +
            plain(FieldLabel.LOCATION to event.location, FieldLabel.DESCRIPTION to event.description)

    private fun plain(vararg entries: Pair<FieldLabel, String?>): List<ResultField> = plain(entries.asList())

    private fun plain(entries: List<Pair<FieldLabel, String?>>): List<ResultField> =
        entries.mapNotNull { (label, value) ->
            value?.takeIf { it.isNotBlank() }?.let { ResultField.Plain(label, it) }
        }
}

/** "latitude,longitude" with plain decimals, as both the screen and a geo: URI want them. */
internal fun coordinates(geo: ParsedContent.Geo): String =
    "${geo.latitude.toBigDecimal().toPlainString()},${geo.longitude.toBigDecimal().toPlainString()}"
