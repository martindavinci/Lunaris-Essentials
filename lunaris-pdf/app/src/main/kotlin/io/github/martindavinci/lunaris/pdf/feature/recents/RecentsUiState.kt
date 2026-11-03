package io.github.martindavinci.lunaris.pdf.feature.recents

import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import io.github.martindavinci.lunaris.pdf.recents.RecentsHome

/** A short message about something that just happened. */
sealed interface RecentsNotice {
    data object PinLimit : RecentsNotice
    data object ShareFailed : RecentsNotice

    /** With the entry, to offer it back. */
    data class Removed(val entry: RecentEntry) : RecentsNotice
}

/**
 * [openReader]: the document is ready, go to the Reader. [entryGone]: a tapped entry no longer opens.
 * [query]: what the list is being searched for, null when it is not.
 */
data class RecentsUiState(
    val entries: List<RecentEntry> = emptyList(),
    val home: RecentsHome = RecentsHome(),
    val query: String? = null,
    val openReader: Boolean = false,
    val entryGone: Boolean = false,
    val notice: RecentsNotice? = null,
    val shareUri: String? = null,
)

sealed interface RecentsAction {
    data class OpenPicked(val uri: String) : RecentsAction
    data class OpenEntry(val entry: RecentEntry) : RecentsAction
    data object ReaderOpened : RecentsAction
    data object MessageShown : RecentsAction
    data object ClearAll : RecentsAction
    data object OpenSearch : RecentsAction
    data object CloseSearch : RecentsAction
    data class SetQuery(val query: String) : RecentsAction
    data class SetPinned(val entry: RecentEntry, val on: Boolean) : RecentsAction
    data class RemoveEntry(val entry: RecentEntry) : RecentsAction
    data class UndoRemove(val entry: RecentEntry) : RecentsAction
    data class ShareEntry(val entry: RecentEntry) : RecentsAction
    data object ShareStarted : RecentsAction
    data object NoticeShown : RecentsAction
}
