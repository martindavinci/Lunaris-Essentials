package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.lifecycle.SavedStateHandle
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The edits live only in the page. This keeps it known elsewhere that there are some: for a document arriving
 * from another app, which must not replace them, and for the app coming back after Android closed it.
 */
internal class UnsavedMark(
    scope: CoroutineScope,
    private val session: CurrentDocument,
    private val saved: SavedStateHandle,
    state: StateFlow<ReaderUiState>,
    update: ((ReaderUiState) -> ReaderUiState) -> Unit,
) {
    init {
        if (saved.get<Boolean>(KEY) == true) update { it.copy(notice = ReaderNotice.EDITS_LOST) }
        scope.launch {
            state.map { it.hasUnsavedEdits }.distinctUntilChanged().collect { unsaved ->
                session.hasUnsavedEdits = unsaved
                saved[KEY] = unsaved
            }
        }
    }

    fun readerClosed() {
        session.hasUnsavedEdits = false
    }

    private companion object {
        const val KEY = "had_unsaved_edits"
    }
}
