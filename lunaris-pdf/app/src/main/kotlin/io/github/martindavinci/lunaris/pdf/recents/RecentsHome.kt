package io.github.martindavinci.lunaris.pdf.recents

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** When a document was opened, to be worded by the screen. */
sealed interface WhenLabel {
    data class At(val millis: Long) : WhenLabel
    data object Yesterday : WhenLabel
    data class Weekday(val day: DayOfWeek) : WhenLabel
    data class Day(val date: LocalDate) : WhenLabel
}

/**
 * What the first screen shows. Usually a document is opened again, so the newest one gets a place of
 * its own in [resume], and stays in [all] with the others. Pinned documents are listed apart, above.
 * With a search, [results] replaces all the rest.
 */
data class RecentsHome(
    val pinned: List<RecentEntry> = emptyList(),
    val resume: RecentEntry? = null,
    val all: List<RecentEntry> = emptyList(),
    val results: List<RecentEntry>? = null,
) {
    companion object {
        private const val DAYS_IN_A_WEEK = 7L

        fun of(entries: List<RecentEntry>, query: String?): RecentsHome {
            if (query != null) {
                return RecentsHome(results = entries.filter { it.name.contains(query.trim(), ignoreCase = true) })
            }
            val (pinned, others) = entries.partition { it.pinned }
            return RecentsHome(pinned = pinned, resume = others.firstOrNull(), all = others)
        }

        /** Null when the time is not known. */
        fun whenLabel(millis: Long, nowMillis: Long, zone: ZoneId): WhenLabel? {
            if (millis <= 0) return null
            val date = dateOf(millis, zone)
            val days = ChronoUnit.DAYS.between(date, dateOf(nowMillis, zone))
            return when {
                days <= 0 -> WhenLabel.At(millis)
                days == 1L -> WhenLabel.Yesterday
                days < DAYS_IN_A_WEEK -> WhenLabel.Weekday(date.dayOfWeek)
                else -> WhenLabel.Day(date)
            }
        }

        private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    }
}
