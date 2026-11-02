package io.github.martindavinci.lunaris.scanner.feature.result

import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.parse.UrlSafety

/** What the user can do with a scanned code. Nothing runs until the user taps it. */
sealed interface ResultAction {
    data class OpenUrl(val url: String) : ResultAction
    data class ConnectWifi(val wifi: ParsedContent.Wifi) : ResultAction
    data class CopyPassword(val password: String) : ResultAction
    data class AddContact(val contact: ParsedContent.Contact) : ResultAction
    data class SendEmail(val email: ParsedContent.Email) : ResultAction
    data class Dial(val number: String) : ResultAction
    data class SendSms(val sms: ParsedContent.Sms) : ResultAction
    data class ViewMap(val geo: ParsedContent.Geo) : ResultAction
    data class AddEvent(val event: ParsedContent.Event) : ResultAction
    data class WebSearch(val query: String) : ResultAction
    data class Copy(val text: String, val sensitive: Boolean) : ResultAction
    data class Share(val text: String) : ResultAction
}

object ResultActions {
    /** The actions for [content]: those of its type, then copy and share of [rawText]. */
    fun forContent(content: ParsedContent, rawText: String): List<ResultAction> =
        typedActions(content) + listOf(
            ResultAction.Copy(rawText, sensitive = content.secret != null),
            ResultAction.Share(rawText),
        )

    private fun typedActions(content: ParsedContent): List<ResultAction> = when (content) {
        is ParsedContent.Url ->
            if (UrlSafety.isOpenable(content.url)) listOf(ResultAction.OpenUrl(content.url)) else emptyList()
        is ParsedContent.Wifi ->
            listOfNotNull(
                ResultAction.ConnectWifi(content).takeIf { content.ssid.isNotEmpty() },
                content.secret?.let { ResultAction.CopyPassword(it) },
            )
        is ParsedContent.Contact -> listOf(ResultAction.AddContact(content))
        is ParsedContent.Email -> listOf(ResultAction.SendEmail(content))
        is ParsedContent.Phone -> listOf(ResultAction.Dial(content.number))
        is ParsedContent.Sms -> listOf(ResultAction.SendSms(content))
        is ParsedContent.Geo -> listOf(ResultAction.ViewMap(content))
        is ParsedContent.Event -> listOf(ResultAction.AddEvent(content))
        is ParsedContent.Product -> listOf(ResultAction.WebSearch(content.code))
        is ParsedContent.Text -> emptyList()
    }
}

/** The password inside [this], if any: the raw text of such a code holds it too. */
internal val ParsedContent.secret: String?
    get() = (this as? ParsedContent.Wifi)?.password?.takeIf { it.isNotEmpty() }
