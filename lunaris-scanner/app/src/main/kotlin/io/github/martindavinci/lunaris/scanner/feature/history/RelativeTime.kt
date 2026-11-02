package io.github.martindavinci.lunaris.scanner.feature.history

sealed interface RelativeLabel {
    data object Now : RelativeLabel
    data class Minutes(val count: Int) : RelativeLabel
    data class Hours(val count: Int) : RelativeLabel
    data object Yesterday : RelativeLabel
    data class Days(val count: Int) : RelativeLabel
    data class Older(val millis: Long) : RelativeLabel
}

object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val TWO_DAYS = 2 * DAY
    private const val MONTH = 30 * DAY

    /** A coarse "time ago", or null for an unknown time (a timestamp that is not positive). */
    fun of(thenMillis: Long, nowMillis: Long): RelativeLabel? {
        if (thenMillis <= 0L) return null
        val elapsed = (nowMillis - thenMillis).coerceAtLeast(0L)
        return when {
            elapsed < MINUTE -> RelativeLabel.Now
            elapsed < HOUR -> RelativeLabel.Minutes((elapsed / MINUTE).toInt())
            elapsed < DAY -> RelativeLabel.Hours((elapsed / HOUR).toInt())
            elapsed < TWO_DAYS -> RelativeLabel.Yesterday
            elapsed < MONTH -> RelativeLabel.Days((elapsed / DAY).toInt())
            else -> RelativeLabel.Older(thenMillis)
        }
    }
}
