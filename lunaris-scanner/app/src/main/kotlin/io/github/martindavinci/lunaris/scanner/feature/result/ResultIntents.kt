package io.github.martindavinci.lunaris.scanner.feature.result

import android.app.SearchManager
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.annotation.RequiresApi
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.WifiSecurity

private const val SCHEME_MAILTO = "mailto"
private const val SCHEME_TEL = "tel"
private const val SCHEME_SMSTO = "smsto"
private const val EXTRA_SMS_BODY = "sms_body"
private const val MIME_TEXT = "text/plain"

private val PHONE_EXTRAS = listOf(
    ContactsContract.Intents.Insert.PHONE,
    ContactsContract.Intents.Insert.SECONDARY_PHONE,
    ContactsContract.Intents.Insert.TERTIARY_PHONE,
)
private val EMAIL_EXTRAS = listOf(
    ContactsContract.Intents.Insert.EMAIL,
    ContactsContract.Intents.Insert.SECONDARY_EMAIL,
    ContactsContract.Intents.Insert.TERTIARY_EMAIL,
)

/** The intents of the result actions. None of them acts without the user confirming in the target app. */
internal object ResultIntents {
    // normalizeScheme: intent filters are case sensitive, and a code may say "HTTPS://".
    fun openUrl(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url).normalizeScheme()).addCategory(Intent.CATEGORY_BROWSABLE)

    fun wifiSettings(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS)

    /** Null when the system refuses the network, for instance an SSID longer than the limit. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun addWifiNetwork(wifi: ParsedContent.Wifi): Intent? = try {
        val builder = WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).setIsHiddenSsid(wifi.hidden)
        if (wifi.security == WifiSecurity.WPA) wifi.password?.let(builder::setWpa2Passphrase)
        Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
            .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(builder.build()))
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    fun addContact(contact: ParsedContent.Contact): Intent {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
        intent.putOptional(ContactsContract.Intents.Insert.NAME, contact.name)
        intent.putOptional(ContactsContract.Intents.Insert.COMPANY, contact.organization)
        intent.putOptional(ContactsContract.Intents.Insert.JOB_TITLE, contact.title)
        intent.putOptional(ContactsContract.Intents.Insert.POSTAL, contact.address)
        intent.putOptional(ContactsContract.Intents.Insert.NOTES, contact.note)
        PHONE_EXTRAS.zip(contact.phones).forEach { (key, value) -> intent.putExtra(key, value) }
        EMAIL_EXTRAS.zip(contact.emails).forEach { (key, value) -> intent.putExtra(key, value) }
        val rows = contactDataRows(contact)
        if (rows.isNotEmpty()) intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
        return intent
    }

    fun sendEmail(email: ParsedContent.Email): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.fromParts(SCHEME_MAILTO, email.to, null))
            .putOptional(Intent.EXTRA_SUBJECT, email.subject)
            .putOptional(Intent.EXTRA_TEXT, email.body)

    fun dial(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts(SCHEME_TEL, number, null))

    fun sendSms(sms: ParsedContent.Sms): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.fromParts(SCHEME_SMSTO, sms.number, null))
            .putOptional(EXTRA_SMS_BODY, sms.body)

    /** Without a query the coordinates are the query: most map apps only drop a pin for one. */
    fun viewMap(geo: ParsedContent.Geo): Intent {
        val position = coordinates(geo)
        val query = Uri.encode(geo.query?.takeIf { it.isNotBlank() } ?: position)
        return Intent(Intent.ACTION_VIEW, Uri.parse("geo:$position?q=$query"))
    }

    fun addEvent(event: ParsedContent.Event): Intent {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putOptional(CalendarContract.Events.TITLE, event.summary)
            .putOptional(CalendarContract.Events.EVENT_LOCATION, event.location)
            .putOptional(CalendarContract.Events.DESCRIPTION, event.description)
        event.startMillis?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        event.endMillis?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        return intent
    }

    fun webSearch(query: String): Intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)

    fun share(text: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType(MIME_TEXT).putExtra(Intent.EXTRA_TEXT, text),
        null,
    )

    /** What the contact extras have no room for: the website, and phones or emails past the third. */
    private fun contactDataRows(contact: ParsedContent.Contact): ArrayList<ContentValues> {
        val rows = ArrayList<ContentValues>()
        contact.phones.drop(PHONE_EXTRAS.size).forEach {
            rows += dataRow(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                it,
            )
        }
        contact.emails.drop(EMAIL_EXTRAS.size).forEach {
            rows += dataRow(
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                it,
            )
        }
        contact.website?.let {
            rows += dataRow(
                ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Website.URL,
                it,
            )
        }
        return rows
    }

    private fun dataRow(mimeType: String, column: String, value: String) = ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, mimeType)
        put(column, value)
    }

    private fun Intent.putOptional(key: String, value: String?): Intent = apply {
        if (!value.isNullOrBlank()) putExtra(key, value)
    }
}
