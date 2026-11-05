package io.github.martindavinci.lunaris.scanner.document

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NAME_START = "lunaris-scan"

// Locale.ROOT keeps the digits Latin whatever language the phone is in: this is a file name, not a text to read.
private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

/** The name suggested when saving, different at every save so that scans do not end up as "(1)", "(2)". */
fun scanFileName(extension: String, at: LocalDateTime): String = "$NAME_START-${STAMP.format(at)}.$extension"
