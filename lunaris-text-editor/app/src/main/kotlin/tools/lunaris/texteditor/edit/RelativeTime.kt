package tools.lunaris.texteditor.edit

sealed interface RelativeLabel {
    data object Now : RelativeLabel
    data class Minutes(val n: Int) : RelativeLabel
    data class Hours(val n: Int) : RelativeLabel
    data object Yesterday : RelativeLabel
    data class Days(val n: Int) : RelativeLabel
    data class Older(val millis: Long) : RelativeLabel
}

object RelativeTime {
    private const val MIN = 60_000L
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    /** Coarse "time ago" label, or null for an unknown time (timestamp <= 0). */
    fun of(thenMillis: Long, nowMillis: Long): RelativeLabel? {
        if (thenMillis <= 0L) return null
        val d = (nowMillis - thenMillis).coerceAtLeast(0)
        return when {
            d < MIN -> RelativeLabel.Now
            d < HOUR -> RelativeLabel.Minutes((d / MIN).toInt())
            d < DAY -> RelativeLabel.Hours((d / HOUR).toInt())
            d < 2 * DAY -> RelativeLabel.Yesterday
            d < 30 * DAY -> RelativeLabel.Days((d / DAY).toInt())
            else -> RelativeLabel.Older(thenMillis)
        }
    }
}
