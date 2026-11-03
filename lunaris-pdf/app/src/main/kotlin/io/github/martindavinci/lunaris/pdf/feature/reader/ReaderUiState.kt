package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState
import io.github.martindavinci.lunaris.pdf.url.UrlSafety
import io.github.martindavinci.lunaris.pdf.url.UrlWarning
import io.github.martindavinci.lunaris.pdf.url.withVisibleBidiControls
import io.github.martindavinci.lunaris.pdf.viewer.OutlineItem

/** A new [viewRevision] asks for a new WebView, after the system took the old one's process. */
data class ReaderUiState(
    val uri: String? = null,
    val name: String = "",
    val page: Int = 1,
    val pages: Int = 0,
    val loading: Boolean = true,
    val error: ReaderError? = null,
    val chromeVisible: Boolean = true,
    val search: SearchState? = null,
    val outline: List<OutlineItem> = emptyList(),
    val passwordPrompt: PasswordPrompt? = null,
    val pendingLink: PendingLink? = null,
    val night: Boolean = false,
    val readAloud: ReadAloudState = ReadAloudState.Idle,
    val shareUri: String? = null,
    val notice: ReaderNotice? = null,
    val canPrint: Boolean = true,
    val viewRevision: Int = 0,
    val topButtonEnabled: Boolean = true,
) {
    val showTopButton: Boolean get() = topButtonEnabled && page > 1
}

enum class ReaderError { CANNOT_OPEN }

/** A short message about something that just happened. */
enum class ReaderNotice { SAVED, SAVE_FAILED, SHARE_FAILED }

data class SearchState(
    val query: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val searching: Boolean = false,
)

/** [wrong]: the last password did not open the document. */
data class PasswordPrompt(val wrong: Boolean)

/** A link tapped in the document, not opened until the user says so. [shownUrl] makes hidden characters visible. */
data class PendingLink(
    val url: String,
    val shownUrl: String,
    val warnings: Set<UrlWarning>,
    val canOpen: Boolean,
) {
    companion object {
        fun of(url: String) = PendingLink(
            url = url,
            shownUrl = url.withVisibleBidiControls(),
            warnings = UrlSafety.warnings(url),
            canOpen = UrlSafety.isOpenable(url),
        )
    }
}

sealed interface ReaderAction {
    data object BackToTop : ReaderAction
    data class GoToPage(val page: Int) : ReaderAction
    data object OpenSearch : ReaderAction
    data object CloseSearch : ReaderAction
    data class SubmitSearch(val query: String) : ReaderAction
    data object FindNext : ReaderAction
    data object FindPrevious : ReaderAction
    data class SubmitPassword(val password: String) : ReaderAction
    data object DismissLink : ReaderAction
    data object ToggleNight : ReaderAction
    data object StartReadAloud : ReaderAction
    data object PauseReadAloud : ReaderAction
    data object ResumeReadAloud : ReaderAction
    data object NextReadAloudPage : ReaderAction
    data object PreviousReadAloudPage : ReaderAction
    data object StopReadAloud : ReaderAction
    data class SaveCopyTo(val uri: String) : ReaderAction
    data object Share : ReaderAction
    data object ShareStarted : ReaderAction
    data object NoticeShown : ReaderAction
}
