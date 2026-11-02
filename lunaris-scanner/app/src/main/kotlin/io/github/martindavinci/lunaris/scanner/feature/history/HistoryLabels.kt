package io.github.martindavinci.lunaris.scanner.feature.history

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import java.text.DateFormat
import java.util.Date

internal fun HistoryKind.icon(): ImageVector = when (this) {
    HistoryKind.URL -> Icons.Filled.Link
    HistoryKind.WIFI -> Icons.Filled.Wifi
    HistoryKind.CONTACT -> Icons.Filled.Person
    HistoryKind.EMAIL -> Icons.Filled.Email
    HistoryKind.PHONE -> Icons.Filled.Phone
    HistoryKind.SMS -> Icons.Filled.Sms
    HistoryKind.GEO -> Icons.Filled.Place
    HistoryKind.EVENT -> Icons.Filled.Event
    HistoryKind.PRODUCT -> Icons.Filled.ShoppingCart
    HistoryKind.TEXT -> Icons.AutoMirrored.Filled.Notes
}

@StringRes
internal fun HistoryKind.labelRes(): Int = when (this) {
    HistoryKind.URL -> R.string.history_kind_url
    HistoryKind.WIFI -> R.string.history_kind_wifi
    HistoryKind.CONTACT -> R.string.history_kind_contact
    HistoryKind.EMAIL -> R.string.history_kind_email
    HistoryKind.PHONE -> R.string.history_kind_phone
    HistoryKind.SMS -> R.string.history_kind_sms
    HistoryKind.GEO -> R.string.history_kind_geo
    HistoryKind.EVENT -> R.string.history_kind_event
    HistoryKind.PRODUCT -> R.string.history_kind_product
    HistoryKind.TEXT -> R.string.history_kind_text
}

@StringRes
internal fun CodeFormat.labelRes(): Int = when (this) {
    CodeFormat.QR_CODE -> R.string.history_format_qr_code
    CodeFormat.AZTEC -> R.string.history_format_aztec
    CodeFormat.DATA_MATRIX -> R.string.history_format_data_matrix
    CodeFormat.PDF_417 -> R.string.history_format_pdf_417
    CodeFormat.MAXICODE -> R.string.history_format_maxicode
    CodeFormat.EAN_13 -> R.string.history_format_ean_13
    CodeFormat.EAN_8 -> R.string.history_format_ean_8
    CodeFormat.UPC_A -> R.string.history_format_upc_a
    CodeFormat.UPC_E -> R.string.history_format_upc_e
    CodeFormat.CODE_128 -> R.string.history_format_code_128
    CodeFormat.CODE_39 -> R.string.history_format_code_39
    CodeFormat.CODE_93 -> R.string.history_format_code_93
    CodeFormat.CODABAR -> R.string.history_format_codabar
    CodeFormat.ITF -> R.string.history_format_itf
    CodeFormat.RSS_14 -> R.string.history_format_rss_14
    CodeFormat.RSS_EXPANDED -> R.string.history_format_rss_expanded
}

/** The time of an entry, or null when it is not known. */
@Composable
internal fun relativeDate(timestampMillis: Long, nowMillis: Long): String? =
    when (val label = RelativeTime.of(timestampMillis, nowMillis)) {
        RelativeLabel.Now -> stringResource(R.string.history_time_now)
        is RelativeLabel.Minutes -> stringResource(R.string.history_time_minutes, label.count)
        is RelativeLabel.Hours -> stringResource(R.string.history_time_hours, label.count)
        RelativeLabel.Yesterday -> stringResource(R.string.history_time_yesterday)
        is RelativeLabel.Days -> stringResource(R.string.history_time_days, label.count)
        is RelativeLabel.Older -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(label.millis))
        null -> null
    }
