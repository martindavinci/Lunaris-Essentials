package io.github.martindavinci.lunaris.scanner.parse

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Event
import io.github.martindavinci.lunaris.scanner.model.ParsedContent.Text
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

class EventContentTest {
    private val originalZone = TimeZone.getDefault()
    private val localZone = ZoneId.of("Asia/Tokyo")

    @Before fun useKnownZone() = TimeZone.setDefault(TimeZone.getTimeZone(localZone))

    @After fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun event(vararg lines: String) =
        parse((listOf("BEGIN:VEVENT") + lines + "END:VEVENT").joinToString("\r\n")) as Event

    private fun utcMillis(hour: Int, minute: Int) =
        LocalDateTime.of(2026, 12, 25, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun localMillis(hour: Int, minute: Int) =
        LocalDateTime.of(2026, 12, 25, hour, minute).atZone(localZone).toInstant().toEpochMilli()

    @Test fun fullEventWithoutCalendar() {
        val parsed = event(
            "SUMMARY:Launch",
            "DTSTART:20261225T100000Z",
            "DTEND:20261225T113000Z",
            "LOCATION:Milano",
            "DESCRIPTION:Bring cake",
        )
        assertThat(parsed).isEqualTo(
            Event(
                summary = "Launch",
                startMillis = utcMillis(10, 0),
                endMillis = utcMillis(11, 30),
                location = "Milano",
                description = "Bring cake",
            ),
        )
    }

    @Test fun eventInsideACalendar() {
        val text = "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:x\nSUMMARY:not this\n" +
            "BEGIN:VEVENT\nSUMMARY:This\nEND:VEVENT\nEND:VCALENDAR"
        assertThat((parse(text) as Event).summary).isEqualTo("This")
    }

    @Test fun onlyTheFirstEventCounts() {
        val text = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:One\nEND:VEVENT\n" +
            "BEGIN:VEVENT\nSUMMARY:Two\nLOCATION:x\nEND:VEVENT\nEND:VCALENDAR"
        val parsed = parse(text) as Event
        assertThat(parsed.summary).isEqualTo("One")
        assertThat(parsed.location).isNull()
    }

    @Test fun timeZoneDefinitionsDoNotLeakIntoTheEvent() {
        val text = "BEGIN:VCALENDAR\nBEGIN:VTIMEZONE\nBEGIN:STANDARD\nDTSTART:19700101T000000\n" +
            "END:STANDARD\nEND:VTIMEZONE\nBEGIN:VEVENT\nSUMMARY:x\nEND:VEVENT\nEND:VCALENDAR"
        assertThat((parse(text) as Event).startMillis).isNull()
    }

    @Test fun alarmsDoNotLeakIntoTheEvent() {
        val parsed = event(
            "BEGIN:VALARM",
            "DESCRIPTION:Reminder",
            "SUMMARY:Alarm",
            "END:VALARM",
            "SUMMARY:Real",
            "DESCRIPTION:Real text",
        )
        assertThat(parsed.summary).isEqualTo("Real")
        assertThat(parsed.description).isEqualTo("Real text")
    }

    @Test fun dateOnlyIsLocalMidnight() {
        val expected = LocalDate.of(2026, 12, 25).atStartOfDay(localZone).toInstant().toEpochMilli()
        assertThat(event("DTSTART:20261225").startMillis).isEqualTo(expected)
        assertThat(event("DTSTART;VALUE=DATE:20261225").startMillis).isEqualTo(expected)
    }

    @Test fun floatingTimeIsLocal() {
        val parsed = event("DTSTART:20261225T100000", "DTEND:20261225T110000")
        assertThat(parsed.startMillis).isEqualTo(localMillis(10, 0))
        assertThat(parsed.endMillis).isEqualTo(localMillis(11, 0))
    }

    @Test fun tzidSetsTheZoneOfTheTime() {
        val parsed = event("DTSTART;TZID=America/New_York:20261225T100000")
        assertThat(parsed.startMillis).isEqualTo(Instant.parse("2026-12-25T15:00:00Z").toEpochMilli())
    }

    @Test fun quotedTzidAndOtherParametersAreFine() {
        val parsed = event("DTSTART;VALUE=DATE-TIME;TZID=\"Asia/Tokyo\":20261225T100000")
        assertThat(parsed.startMillis).isEqualTo(Instant.parse("2026-12-25T01:00:00Z").toEpochMilli())
    }

    @Test fun unknownTzidCountsAsLocal() {
        val parsed = event("DTSTART;TZID=Eastern Standard Time:20261225T100000")
        assertThat(parsed.startMillis).isEqualTo(localMillis(10, 0))
    }

    @Test fun yearTooLargeForTheClockIsNotADate() {
        listOf(
            "DTSTART:+9999999990101T000000Z",
            "DTSTART:+9999999990101T000000",
            "DTSTART;TZID=America/New_York:-9999999990101T000000",
        ).forEach { line ->
            assertThat(event("SUMMARY:Launch", line)).isEqualTo(Event("Launch", null, null, null, null))
        }
        assertThat(parse("BEGIN:VEVENT\nDTSTART:+9999999990101T000000Z"))
            .isEqualTo(Text("BEGIN:VEVENT\nDTSTART:+9999999990101T000000Z"))
    }

    @Test fun utcFormIgnoresTheLocalZone() {
        assertThat(event("DTSTART:19700101T000000Z").startMillis).isEqualTo(0L)
    }

    @Test fun badDatesBecomeNullWhenTheRestIsUsable() {
        val badDates = listOf(
            "garbage",
            "2026",
            "20261301",
            "20260230",
            "20261225T250000",
            "20261225T100000X",
            "20261225T100000ZZ",
            "2026-12-25",
            "",
        )
        badDates.forEach { date ->
            assertThat(event("SUMMARY:x", "DTSTART:$date").startMillis).isNull()
        }
    }

    @Test fun missingFieldsAreNull() {
        assertThat(event("DTSTART:19700101T000000Z")).isEqualTo(Event(null, 0L, null, null, null))
    }

    @Test fun foldingAndEscapes() {
        val parsed = event(
            "SUMMARY:Long\r\n  title",
            """LOCATION:Via Roma\, 1\; Milano""",
            """DESCRIPTION:one\ntwo\\three""",
        )
        assertThat(parsed.summary).isEqualTo("Long title")
        assertThat(parsed.location).isEqualTo("Via Roma, 1; Milano")
        assertThat(parsed.description).isEqualTo("one\ntwo\\three")
    }

    @Test fun lowercaseAndBareLineFeeds() {
        val parsed = parse("begin:vevent\nsummary:x\ndtstart:19700101T000000Z\nend:vevent") as Event
        assertThat(parsed.summary).isEqualTo("x")
        assertThat(parsed.startMillis).isEqualTo(0L)
    }

    @Test fun missingEndIsTolerated() {
        assertThat((parse("BEGIN:VEVENT\nSUMMARY:x") as Event).summary).isEqualTo("x")
    }

    @Test fun whatFollowsEndIsIgnored() {
        val parsed = parse("BEGIN:VEVENT\nSUMMARY:x\nEND:VEVENT\nLOCATION:y") as Event
        assertThat(parsed.location).isNull()
    }

    @Test fun withNothingUsefulItIsText() {
        val malformed = listOf(
            "BEGIN:VEVENT",
            "BEGIN:VEVENT\nEND:VEVENT",
            "BEGIN:VEVENT\nDTSTART:garbage\nEND:VEVENT",
            "BEGIN:VEVENT\nUID:1\nEND:VEVENT",
            "BEGIN:VEVENTS\nSUMMARY:x\nEND:VEVENT",
            "BEGIN:VCALENDAR",
            "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR",
            "BEGIN:VCALENDAR\nBEGIN:VTODO\nSUMMARY:x\nEND:VTODO\nEND:VCALENDAR",
        )
        malformed.forEach { assertThat(parse(it)).isEqualTo(Text(it)) }
    }
}
