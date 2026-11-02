package io.github.martindavinci.lunaris.scanner.feature.result

import io.github.martindavinci.lunaris.scanner.model.ParsedContent
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.model.UrlWarning
import io.github.martindavinci.lunaris.scanner.parse.ContentParser
import io.github.martindavinci.lunaris.scanner.parse.UrlSafety

data class ResultUiState(
    val code: ScannedCode? = null,
    val content: ParsedContent? = null,
    val fields: List<ResultField> = emptyList(),
    val urlWarnings: List<UrlWarning> = emptyList(),
    val urlBlocked: Boolean = false,
    val actions: List<ResultAction> = emptyList(),
    val secretRevealed: Boolean = false,
) {
    /** The raw text of a Wi-Fi code spells out the password, so it stays masked along with it. */
    val rawTextHidden: Boolean
        get() = !secretRevealed && content?.secret != null
}

sealed interface ResultUiAction {
    data object ToggleSecret : ResultUiAction
}

fun resultUiState(code: ScannedCode?, secretRevealed: Boolean): ResultUiState {
    if (code == null) return ResultUiState()
    val content = ContentParser.parse(code)
    val url = (content as? ParsedContent.Url)?.url
    return ResultUiState(
        code = code,
        content = content,
        fields = ResultFields.forContent(content),
        urlWarnings = url?.let { UrlSafety.warnings(it).sorted() }.orEmpty(),
        urlBlocked = url != null && !UrlSafety.isOpenable(url),
        actions = ResultActions.forContent(content, code.text),
        secretRevealed = secretRevealed,
    )
}
