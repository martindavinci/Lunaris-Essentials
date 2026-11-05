package io.github.martindavinci.lunaris.scanner.feature.scan

import io.github.martindavinci.lunaris.scanner.feature.history.oneLine
import io.github.martindavinci.lunaris.scanner.feature.result.secret
import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.model.UrlWarning
import io.github.martindavinci.lunaris.scanner.parse.ContentParser
import io.github.martindavinci.lunaris.scanner.parse.UrlSafety

/** A code read in this session, and when: the newest stands first and is the one on the card. */
data class SessionCode(val code: ScannedCode, val readAtMillis: Long)

/** The main button of a card. Only a link with nothing wrong opens at one tap: the rest needs its full page. */
sealed interface CardAction {
    data class OpenLink(val url: String) : CardAction

    data object OpenDetails : CardAction

    data object None : CardAction
}

data class CodeCard(
    val code: ScannedCode,
    val content: ParsedContent,
    val summary: String,
    val warnings: List<UrlWarning>,
    val blocked: Boolean,
    val main: CardAction,
    val sensitive: Boolean,
)

fun codeCard(code: ScannedCode): CodeCard {
    val content = ContentParser.parse(code)
    val url = (content as? ParsedContent.Url)?.url
    val warnings = url?.let { UrlSafety.warnings(it).sorted() }.orEmpty()
    val blocked = url != null && !UrlSafety.isOpenable(url)
    return CodeCard(
        code = code,
        content = content,
        summary = content.oneLine(),
        warnings = warnings,
        blocked = blocked,
        main = when {
            url != null && warnings.isEmpty() && !blocked -> CardAction.OpenLink(url)
            content is ParsedContent.Text || content is ParsedContent.Product -> CardAction.None
            else -> CardAction.OpenDetails
        },
        sensitive = content.secret != null,
    )
}

/** Their content as it was read, one per line, as the list shows them. */
fun sessionText(session: List<SessionCode>): String = session.joinToString("\n") { it.code.text }

/** The raw text of a Wi-Fi code spells out the password. */
fun sessionIsSensitive(session: List<SessionCode>): Boolean = session.any { codeCard(it.code).sensitive }
