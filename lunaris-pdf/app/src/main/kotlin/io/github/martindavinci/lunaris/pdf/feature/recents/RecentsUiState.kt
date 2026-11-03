package io.github.martindavinci.lunaris.pdf.feature.recents

import io.github.martindavinci.lunaris.pdf.recents.RecentEntry

/** [openReader]: the document is ready, go to the Reader. [entryGone]: a tapped entry no longer opens. */
data class RecentsUiState(
    val entries: List<RecentEntry> = emptyList(),
    val openReader: Boolean = false,
    val entryGone: Boolean = false,
)

sealed interface RecentsAction {
    data class OpenPicked(val uri: String) : RecentsAction
    data class OpenEntry(val entry: RecentEntry) : RecentsAction
    data object ReaderOpened : RecentsAction
    data object MessageShown : RecentsAction
    data object ClearAll : RecentsAction
}
