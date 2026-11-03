package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.url.UrlSafety
import io.github.martindavinci.lunaris.pdf.url.UrlWarning
import io.github.martindavinci.lunaris.pdf.url.withVisibleBidiControls
import io.github.martindavinci.lunaris.pdf.viewer.DocumentChapters
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
    val shareUri: String? = null,
    val notice: ReaderNotice? = null,
    val canPrint: Boolean = true,
    val viewRevision: Int = 0,
    val topButtonEnabled: Boolean = true,
    val scrolling: Boolean = false,
    val scrollingDown: Boolean = false,
    val jump: PageJump? = null,
    val chapterTicks: Int = 0,
    val canWrite: Boolean = false,
    val annotation: AnnotationUiState? = null,
    val edits: DocumentEdits = DocumentEdits(),
    val saveSheet: SaveSheet? = null,
    /** Sending the document on with its changes would break its digital signature: asked first. */
    val signedWarning: Boolean = false,
    /** How far a save has come, from 0 to 1, while there is one. */
    val exportProgress: Float? = null,
    val printUri: String? = null,
    val closeReader: Boolean = false,
) {
    /** Past the first pages, when the way back is worth a button. Never in the way of going on. */
    val showTopButton: Boolean
        get() = topButtonEnabled && page >= TOP_BUTTON_FROM_PAGE && !scrollingDown && search == null && annotation == null

    val showPageIndicator: Boolean get() = !chromeVisible && scrolling && pages > 0

    val showJump: Boolean get() = jump != null && search == null && annotation == null

    val hasUnsavedEdits: Boolean get() = edits.changes > 0

    /** The chapter being read, when the document has contents. */
    val chapter: String? get() = DocumentChapters.chapterAt(outline, page)?.title

    private companion object {
        const val TOP_BUTTON_FROM_PAGE = 3
    }
}

enum class ReaderError { CANNOT_OPEN }

/** A short message about something that just happened. */
enum class ReaderNotice { SAVED, SAVE_FAILED, SHARE_FAILED, EDITS_LOST }

data class SearchState(
    val query: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val searching: Boolean = false,
    val caseSensitive: Boolean = false,
    val wholeWords: Boolean = false,
    /** The page of the current match, and how many matches it has. */
    val page: Int = 0,
    val onPage: Int = 0,
    /** Where reading was when the search opened. */
    val startPage: Int = 1,
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
    data object ToggleMatchCase : ReaderAction
    data object ToggleWholeWords : ReaderAction
    data object ReturnFromJump : ReaderAction
    data class SetBarSpace(val top: Int, val bottom: Int) : ReaderAction
    data class SubmitPassword(val password: String) : ReaderAction
    data object DismissLink : ReaderAction
    data object ToggleNight : ReaderAction
    data class SaveCopyTo(val uri: String) : ReaderAction
    data object Share : ReaderAction
    data object ShareStarted : ReaderAction
    data object NoticeShown : ReaderAction
}
