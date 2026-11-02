package io.github.martindavinci.lunaris.scanner.parse

import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private const val VEVENT = "VEVENT"
private const val DATE_LENGTH = 8
private const val UTC_MARKER = 'Z'
private const val TZID_PARAMETER = "TZID"

private val dateFormat = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)
private val dateTimeFormat =
    DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT)

private val emptyEvent = ParsedContent.Event(null, null, null, null, null)

/** The first VEVENT of the text, inside a VCALENDAR or not. Nothing usable means it is not an event. */
internal fun parseEvent(text: String): ParsedContent.Event? {
    val lines = parseContentLines(text)
    val begin = lines.indexOfFirst { it.isBegin(VEVENT) }
    if (begin < 0) return null
    val properties = eventProperties(lines.drop(begin + 1))
    return ParsedContent.Event(
        summary = properties.firstText("SUMMARY"),
        startMillis = properties.firstDate("DTSTART"),
        endMillis = properties.firstDate("DTEND"),
        location = properties.firstText("LOCATION"),
        description = properties.firstText("DESCRIPTION"),
    ).takeUnless { it == emptyEvent }
}

/** Stops at the end of the event and skips what nests inside it, such as a VALARM and its text. */
private fun eventProperties(lines: List<ContentLine>): List<ContentLine> = buildList {
    var nested: String? = null
    for (line in lines) {
        val component = nested
        when {
            component != null -> if (line.isEnd(component)) nested = null
            line.isEnd(VEVENT) -> break
            line.name == PROPERTY_BEGIN -> nested = line.value.trim()
            else -> add(line)
        }
    }
}

private fun List<ContentLine>.firstDate(name: String): Long? =
    firstOrNull { it.name == name }?.let { parseCalendarDate(it.value.trim(), it.parameter(TZID_PARAMETER)) }

/**
 * A date is local midnight, a `Z` time is UTC, and any other time is in its TZID, or local without
 * a known one. A year can parse and still not fit in the milliseconds, so that is caught too.
 */
private fun parseCalendarDate(value: String, tzid: String?): Long? = try {
    val moment = when {
        value.length == DATE_LENGTH -> LocalDate.parse(value, dateFormat).atStartOfDay(ZoneId.systemDefault())
        value.endsWith(UTC_MARKER) -> LocalDateTime.parse(value.dropLast(1), dateTimeFormat).atZone(ZoneOffset.UTC)
        else -> LocalDateTime.parse(value, dateTimeFormat).atZone(zoneOf(tzid))
    }
    moment.toInstant().toEpochMilli()
} catch (_: DateTimeException) {
    null
} catch (_: ArithmeticException) {
    null
}

private fun zoneOf(tzid: String?): ZoneId = try {
    tzid?.let(ZoneId::of) ?: ZoneId.systemDefault()
} catch (_: DateTimeException) {
    ZoneId.systemDefault()
}
