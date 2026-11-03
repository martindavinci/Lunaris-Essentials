package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Copies of the open file, for saving and sharing, and what the screen hears about them. */
internal class ReaderFiles(
    private val scope: CoroutineScope,
    private val documents: DocumentActions,
    private val update: ((ReaderUiState) -> ReaderUiState) -> Unit,
) {
    fun saveCopy(from: String, to: String) {
        scope.launch {
            val notice = if (documents.saveCopy(from, to)) ReaderNotice.SAVED else ReaderNotice.SAVE_FAILED
            update { it.copy(notice = notice) }
        }
    }

    fun share(from: String, name: String) {
        scope.launch {
            val shared = documents.prepareShare(from, name)
            update { if (shared != null) it.copy(shareUri = shared) else it.copy(notice = ReaderNotice.SHARE_FAILED) }
        }
    }
}
