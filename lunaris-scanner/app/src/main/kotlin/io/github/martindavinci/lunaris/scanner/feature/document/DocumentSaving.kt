package io.github.martindavinci.lunaris.scanner.feature.document

import io.github.martindavinci.lunaris.scanner.document.DocumentFiles
import io.github.martindavinci.lunaris.scanner.document.PictureFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Writes the scan out. [savedSinceChange] says whether leaving now would lose nothing. */
internal class DocumentSaving(
    private val scope: CoroutineScope,
    private val files: DocumentFiles,
    private val state: MutableStateFlow<DocumentUiState>,
) {
    var savedSinceChange = false

    fun savePdf(uri: String) = save(uri, wholeScan = true) { files.savePdf(uri, state.value.pages) }

    fun savePicture(uri: String, format: PictureFormat) {
        val page = state.value.currentPage ?: return
        save(uri, wholeScan = state.value.pages.size == 1) { files.savePicture(uri, page, format) }
    }

    /** Nothing was created in the folder yet, so there is nothing for this side to discard. */
    fun savePictures(folderUri: String, format: PictureFormat) {
        write(wholeScan = true) { files.savePictures(folderUri, state.value.pages, format) }
    }

    fun share() {
        state.update { it.copy(busy = true) }
        scope.launch {
            val uri = files.cachePdfForSharing(state.value.pages)
            state.update {
                it.copy(busy = false, shareUri = uri, message = if (uri == null) DocumentMessage.SHARE_FAILED else it.message)
            }
        }
    }

    private fun save(uri: String, wholeScan: Boolean, work: suspend () -> Boolean) {
        write(wholeScan) { work().also { saved -> if (!saved) files.discard(uri) } }
    }

    /** Only files holding every page make it safe to leave without asking. */
    private fun write(wholeScan: Boolean, work: suspend () -> Boolean) {
        state.update { it.copy(busy = true) }
        scope.launch {
            val saved = state.value.pages.isNotEmpty() && work()
            savedSinceChange = (saved && wholeScan) || savedSinceChange
            state.update { it.copy(busy = false, message = if (saved) DocumentMessage.SAVED else DocumentMessage.SAVE_FAILED) }
        }
    }
}
