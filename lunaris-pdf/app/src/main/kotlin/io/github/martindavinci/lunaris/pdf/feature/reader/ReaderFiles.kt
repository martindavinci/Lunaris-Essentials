package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.actions.DocumentActions

/** Copies of the open file, for saving and sharing, and what the screen hears about them. */
internal class ReaderFiles(
    private val documents: DocumentActions,
    private val update: ((ReaderUiState) -> ReaderUiState) -> Unit,
) {
    suspend fun saveCopy(from: String, to: String) {
        val notice = if (documents.saveCopy(from, to)) ReaderNotice.SAVED else ReaderNotice.SAVE_FAILED
        update { it.copy(notice = notice) }
    }

    suspend fun share(from: String, name: String) {
        val shared = documents.prepareShare(from, name)
        update { if (shared != null) it.copy(shareUri = shared) else it.copy(notice = ReaderNotice.SHARE_FAILED) }
    }
}
