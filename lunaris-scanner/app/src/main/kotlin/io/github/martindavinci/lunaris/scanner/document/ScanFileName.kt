package io.github.martindavinci.lunaris.scanner.document

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NAME_START = "lunaris-scan"

// Locale.ROOT keeps the digits Latin whatever language the phone is in: this is a file name, not a text to read.
private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
private const val PAGE_FORMAT = "%02d"

/**
 * The name suggested when saving, different at every save so that scans do not end up as "(1)", "(2)".
 * With a [page], it is one of several files of the same scan.
 */
fun scanFileName(extension: String, at: LocalDateTime, page: Int? = null): String {
    val number = page?.let { "-" + PAGE_FORMAT.format(Locale.ROOT, it) }.orEmpty()
    return "$NAME_START-${STAMP.format(at)}$number.$extension"
}
