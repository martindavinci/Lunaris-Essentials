package io.github.martindavinci.lunaris.scanner.feature.result

import androidx.annotation.StringRes
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.UrlWarning
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

@get:StringRes
internal val CodeFormat.labelRes: Int
    get() = when (this) {
        CodeFormat.QR_CODE -> R.string.result_format_qr_code
        CodeFormat.AZTEC -> R.string.result_format_aztec
        CodeFormat.DATA_MATRIX -> R.string.result_format_data_matrix
        CodeFormat.PDF_417 -> R.string.result_format_pdf_417
        CodeFormat.MAXICODE -> R.string.result_format_maxicode
        CodeFormat.EAN_13 -> R.string.result_format_ean_13
        CodeFormat.EAN_8 -> R.string.result_format_ean_8
        CodeFormat.UPC_A -> R.string.result_format_upc_a
        CodeFormat.UPC_E -> R.string.result_format_upc_e
        CodeFormat.CODE_128 -> R.string.result_format_code_128
        CodeFormat.CODE_39 -> R.string.result_format_code_39
        CodeFormat.CODE_93 -> R.string.result_format_code_93
        CodeFormat.CODABAR -> R.string.result_format_codabar
        CodeFormat.ITF -> R.string.result_format_itf
        CodeFormat.RSS_14 -> R.string.result_format_rss_14
        CodeFormat.RSS_EXPANDED -> R.string.result_format_rss_expanded
    }

@get:StringRes
internal val ParsedContent.kindRes: Int
    get() = when (this) {
        is ParsedContent.Url -> R.string.result_kind_url
        is ParsedContent.Wifi -> R.string.result_kind_wifi
        is ParsedContent.Contact -> R.string.result_kind_contact
        is ParsedContent.Email -> R.string.result_kind_email
        is ParsedContent.Phone -> R.string.result_kind_phone
        is ParsedContent.Sms -> R.string.result_kind_sms
        is ParsedContent.Geo -> R.string.result_kind_geo
        is ParsedContent.Event -> R.string.result_kind_event
        is ParsedContent.Product -> R.string.result_kind_product
        is ParsedContent.Text -> R.string.result_kind_text
    }

@get:StringRes
internal val FieldLabel.labelRes: Int
    get() = when (this) {
        FieldLabel.URL -> R.string.result_field_url
        FieldLabel.SSID -> R.string.result_field_ssid
        FieldLabel.PASSWORD -> R.string.result_field_password
        FieldLabel.SECURITY -> R.string.result_field_security
        FieldLabel.HIDDEN_NETWORK -> R.string.result_field_hidden_network
        FieldLabel.NAME -> R.string.result_field_name
        FieldLabel.ORGANIZATION -> R.string.result_field_organization
        FieldLabel.JOB_TITLE -> R.string.result_field_job_title
        FieldLabel.PHONE -> R.string.result_field_phone
        FieldLabel.EMAIL -> R.string.result_field_email
        FieldLabel.ADDRESS -> R.string.result_field_address
        FieldLabel.WEBSITE -> R.string.result_field_website
        FieldLabel.NOTE -> R.string.result_field_note
        FieldLabel.TO -> R.string.result_field_to
        FieldLabel.SUBJECT -> R.string.result_field_subject
        FieldLabel.BODY -> R.string.result_field_body
        FieldLabel.NUMBER -> R.string.result_field_number
        FieldLabel.MESSAGE -> R.string.result_field_message
        FieldLabel.COORDINATES -> R.string.result_field_coordinates
        FieldLabel.QUERY -> R.string.result_field_query
        FieldLabel.SUMMARY -> R.string.result_field_summary
        FieldLabel.START -> R.string.result_field_start
        FieldLabel.END -> R.string.result_field_end
        FieldLabel.LOCATION -> R.string.result_field_location
        FieldLabel.DESCRIPTION -> R.string.result_field_description
        FieldLabel.CODE -> R.string.result_field_code
    }

@get:StringRes
internal val WifiSecurity.labelRes: Int
    get() = when (this) {
        WifiSecurity.NONE -> R.string.result_wifi_security_none
        WifiSecurity.WEP -> R.string.result_wifi_security_wep
        WifiSecurity.WPA -> R.string.result_wifi_security_wpa
    }

@get:StringRes
internal val UrlWarning.labelRes: Int
    get() = when (this) {
        UrlWarning.NOT_HTTPS -> R.string.result_warning_not_https
        UrlWarning.PUNYCODE -> R.string.result_warning_punycode
        UrlWarning.NON_ASCII_HOST -> R.string.result_warning_non_ascii_host
        UrlWarning.IP_ADDRESS_HOST -> R.string.result_warning_ip_address_host
        UrlWarning.EMBEDDED_CREDENTIALS -> R.string.result_warning_embedded_credentials
        UrlWarning.UNUSUAL_PORT -> R.string.result_warning_unusual_port
    }

@get:StringRes
internal val ResultAction.labelRes: Int
    get() = when (this) {
        is ResultAction.OpenUrl -> R.string.result_action_open_url
        is ResultAction.ConnectWifi -> R.string.result_action_connect_wifi
        is ResultAction.CopyPassword -> R.string.result_action_copy_password
        is ResultAction.AddContact -> R.string.result_action_add_contact
        is ResultAction.SendEmail -> R.string.result_action_send_email
        is ResultAction.Dial -> R.string.result_action_dial
        is ResultAction.SendSms -> R.string.result_action_send_sms
        is ResultAction.ViewMap -> R.string.result_action_view_map
        is ResultAction.AddEvent -> R.string.result_action_add_event
        is ResultAction.WebSearch -> R.string.result_action_web_search
        is ResultAction.Copy -> R.string.copy
        is ResultAction.Share -> R.string.share
    }
