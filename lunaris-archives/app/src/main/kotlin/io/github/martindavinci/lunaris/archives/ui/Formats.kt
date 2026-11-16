package io.github.martindavinci.lunaris.archives.ui

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val SEPARATOR = " · "
const val PATH_SEPARATOR = " / "
const val RESULT_SEPARATOR = " › "

private const val DAY_MONTH_YEAR = "dMMMyyyy"
private const val DAY_MONTH_YEAR_TIME = "dMMMyyyyHHmm"

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "12.6 MB", in the words and digits of the language in use. */
@Composable
fun sizeText(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** A plural whose %1$s is the number with grouping: "1,204 files". */
@Composable
fun countText(@PluralsRes id: Int, count: Int): String {
    val locale = locale()
    val number = remember(count, locale) { NumberFormat.getIntegerInstance(locale).format(count) }
    return pluralStringResource(id, count, number)
}

/** "64%" in English, "64 %" in German and French. */
@Composable
fun percentText(fraction: Float): String {
    val locale = locale()
    return remember(fraction, locale) { NumberFormat.getPercentInstance(locale).format(fraction.coerceIn(0f, 1f).toDouble()) }
}

/** "3 Nov 2026". */
@Composable
fun entryDate(millis: Long): String = dateText(millis, DAY_MONTH_YEAR)

/** "3 Nov 2026 18:40". */
@Composable
fun entryDateTime(millis: Long): String = dateText(millis, DAY_MONTH_YEAR_TIME)

@Composable
private fun dateText(millis: Long, skeleton: String): String {
    val locale = locale()
    return remember(millis, skeleton, locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(millis))
    }
}
