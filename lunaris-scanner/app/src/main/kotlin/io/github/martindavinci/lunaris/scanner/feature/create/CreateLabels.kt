package io.github.martindavinci.lunaris.scanner.feature.create

import androidx.annotation.StringRes
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.encode.InvalidReason
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

@StringRes
internal fun labelOf(type: CreateType): Int = when (type) {
    CreateType.TEXT -> R.string.create_type_text
    CreateType.URL -> R.string.create_type_url
    CreateType.WIFI -> R.string.create_type_wifi
    CreateType.CONTACT -> R.string.create_type_contact
    CreateType.EMAIL -> R.string.create_type_email
    CreateType.PHONE -> R.string.create_type_phone
    CreateType.SMS -> R.string.create_type_sms
    CreateType.BARCODE -> R.string.create_type_barcode
}

@StringRes
internal fun labelOf(format: CodeFormat): Int = when (format) {
    CodeFormat.QR_CODE -> R.string.create_format_qr_code
    CodeFormat.AZTEC -> R.string.create_format_aztec
    CodeFormat.DATA_MATRIX -> R.string.create_format_data_matrix
    CodeFormat.PDF_417 -> R.string.create_format_pdf_417
    CodeFormat.MAXICODE -> R.string.create_format_maxicode
    CodeFormat.EAN_13 -> R.string.create_format_ean_13
    CodeFormat.EAN_8 -> R.string.create_format_ean_8
    CodeFormat.UPC_A -> R.string.create_format_upc_a
    CodeFormat.UPC_E -> R.string.create_format_upc_e
    CodeFormat.CODE_128 -> R.string.create_format_code_128
    CodeFormat.CODE_39 -> R.string.create_format_code_39
    CodeFormat.CODE_93 -> R.string.create_format_code_93
    CodeFormat.CODABAR -> R.string.create_format_codabar
    CodeFormat.ITF -> R.string.create_format_itf
    CodeFormat.RSS_14 -> R.string.create_format_rss_14
    CodeFormat.RSS_EXPANDED -> R.string.create_format_rss_expanded
}

@StringRes
internal fun labelOf(security: WifiSecurity): Int = when (security) {
    WifiSecurity.NONE -> R.string.create_wifi_security_none
    WifiSecurity.WEP -> R.string.create_wifi_security_wep
    WifiSecurity.WPA -> R.string.create_wifi_security_wpa
}

@StringRes
internal fun messageOf(reason: InvalidReason): Int = when (reason) {
    InvalidReason.EMPTY -> R.string.create_error_empty
    InvalidReason.TOO_LONG -> R.string.create_error_too_long
    InvalidReason.BAD_CHARACTERS -> R.string.create_error_bad_characters
    InvalidReason.BAD_LENGTH -> R.string.create_error_bad_length
    InvalidReason.BAD_CHECK_DIGIT -> R.string.create_error_bad_check_digit
}

@StringRes
internal fun messageOf(message: CreateMessage): Int = when (message) {
    CreateMessage.SAVED -> R.string.create_message_saved
    CreateMessage.SAVE_FAILED -> R.string.create_message_save_failed
    CreateMessage.SHARE_FAILED -> R.string.create_message_share_failed
    CreateMessage.COPIED -> R.string.create_message_copied
}
