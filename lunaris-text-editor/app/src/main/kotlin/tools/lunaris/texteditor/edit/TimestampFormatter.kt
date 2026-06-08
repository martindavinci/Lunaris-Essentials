package tools.lunaris.texteditor.edit

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimestampFormatter {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun format(instant: Instant, zone: ZoneId): String = FORMAT.format(instant.atZone(zone))
}
