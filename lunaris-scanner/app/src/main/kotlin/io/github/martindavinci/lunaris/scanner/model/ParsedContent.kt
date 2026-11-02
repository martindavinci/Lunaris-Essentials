package io.github.martindavinci.lunaris.scanner.model

sealed interface ParsedContent {
    data class Url(val url: String) : ParsedContent

    data class Wifi(
        val ssid: String,
        val password: String?,
        val security: WifiSecurity,
        val hidden: Boolean,
    ) : ParsedContent

    data class Contact(
        val name: String?,
        val organization: String?,
        val title: String?,
        val phones: List<String>,
        val emails: List<String>,
        val address: String?,
        val website: String?,
        val note: String?,
    ) : ParsedContent

    data class Email(val to: String, val subject: String?, val body: String?) : ParsedContent

    data class Phone(val number: String) : ParsedContent

    data class Sms(val number: String, val body: String?) : ParsedContent

    data class Geo(val latitude: Double, val longitude: Double, val query: String?) : ParsedContent

    data class Event(
        val summary: String?,
        val startMillis: Long?,
        val endMillis: Long?,
        val location: String?,
        val description: String?,
    ) : ParsedContent

    data class Product(val code: String, val format: CodeFormat) : ParsedContent

    data class Text(val text: String) : ParsedContent
}

enum class WifiSecurity { NONE, WEP, WPA }
