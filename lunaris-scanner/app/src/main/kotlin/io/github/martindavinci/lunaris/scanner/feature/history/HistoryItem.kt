package io.github.martindavinci.lunaris.scanner.feature.history

import io.github.martindavinci.lunaris.scanner.history.HistoryEntry
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.parse.withVisibleBidiControls

enum class HistoryKind { URL, WIFI, CONTACT, EMAIL, PHONE, SMS, GEO, EVENT, PRODUCT, TEXT }

/**
 * One row of the list. [summary] is what the row shows and may be empty; [searchText] is what the
 * search looks at. Neither holds the password of a Wi-Fi code.
 */
data class HistoryItem(
    val id: Long,
    val code: ScannedCode,
    val kind: HistoryKind,
    val summary: String,
    val searchText: String,
    val timestampMillis: Long,
)

private const val SUMMARY_MAX_CHARS = 200
private val WHITESPACE = Regex("\\s+")

fun HistoryEntry.toItem(content: ParsedContent): HistoryItem = HistoryItem(
    id = id,
    code = ScannedCode(text, format),
    kind = content.kind(),
    summary = content.summary().replace(WHITESPACE, " ").trim().take(SUMMARY_MAX_CHARS).withVisibleBidiControls(),
    searchText = if (content is ParsedContent.Wifi) content.ssid else text,
    timestampMillis = timestampMillis,
)

fun List<HistoryItem>.matching(query: String): List<HistoryItem> {
    val needle = query.trim()
    return if (needle.isEmpty()) this else filter { it.searchText.contains(needle, ignoreCase = true) }
}

private fun ParsedContent.kind(): HistoryKind = when (this) {
    is ParsedContent.Url -> HistoryKind.URL
    is ParsedContent.Wifi -> HistoryKind.WIFI
    is ParsedContent.Contact -> HistoryKind.CONTACT
    is ParsedContent.Email -> HistoryKind.EMAIL
    is ParsedContent.Phone -> HistoryKind.PHONE
    is ParsedContent.Sms -> HistoryKind.SMS
    is ParsedContent.Geo -> HistoryKind.GEO
    is ParsedContent.Event -> HistoryKind.EVENT
    is ParsedContent.Product -> HistoryKind.PRODUCT
    is ParsedContent.Text -> HistoryKind.TEXT
}

private fun ParsedContent.summary(): String = when (this) {
    is ParsedContent.Url -> url
    is ParsedContent.Wifi -> ssid
    is ParsedContent.Contact -> firstPresent(name, organization, phones.firstOrNull(), emails.firstOrNull(), website)
    is ParsedContent.Email -> to
    is ParsedContent.Phone -> number
    is ParsedContent.Sms -> number
    is ParsedContent.Geo -> firstPresent(query, "$latitude, $longitude")
    is ParsedContent.Event -> firstPresent(summary, location)
    is ParsedContent.Product -> code
    is ParsedContent.Text -> text
}

private fun firstPresent(vararg values: String?): String = values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
