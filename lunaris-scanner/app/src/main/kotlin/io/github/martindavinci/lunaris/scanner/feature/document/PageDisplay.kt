package io.github.martindavinci.lunaris.scanner.feature.document

import io.github.martindavinci.lunaris.scanner.document.DocumentPhotos
import io.github.martindavinci.lunaris.scanner.document.PageRendering
import io.github.martindavinci.lunaris.scanner.document.shrunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Long sides on screen: of the photo under the corners, of the page as it will come out, of the small copy in the strip. */
private const val PHOTO_LONG_SIDE = 2000
private const val RESULT_LONG_SIDE = 1200
private const val THUMBNAIL_LONG_SIDE = 200

/** Puts the current page on screen. A new choice replaces whatever was still under way: only the last one gets there. */
internal class PageDisplay(
    private val scope: CoroutineScope,
    private val photos: DocumentPhotos,
    private val rendering: PageRendering,
    private val state: MutableStateFlow<DocumentUiState>,
) {
    private var job: Job? = null

    fun loadPhoto(renderAfter: Boolean) {
        val page = state.value.currentPage ?: return
        job?.cancel()
        state.update { it.copy(photo = null, rendering = renderAfter) }
        job = scope.launch {
            val photo = photos.load(page.photoPath, PHOTO_LONG_SIDE)
            state.update {
                it.copy(photo = photo, rendering = false, message = if (photo == null) DocumentMessage.PAGE_FAILED else it.message)
            }
            if (renderAfter && photo != null) renderNow()
        }
    }

    /** With the photo still on its way, it has to arrive first: stopping it would leave nothing to render. */
    fun render() {
        if (state.value.photo == null) {
            loadPhoto(renderAfter = true)
            return
        }
        job?.cancel()
        job = scope.launch { renderNow() }
    }

    private suspend fun renderNow() {
        val now = state.value
        val page = now.currentPage ?: return
        val photo = now.photo ?: return
        state.update { it.copy(rendering = true) }
        val result = rendering.render(photo, page.settings, page.workSize, RESULT_LONG_SIDE)
        val thumbnail = result?.let { shrunk(it, THUMBNAIL_LONG_SIDE) }
        state.update { current ->
            current.copy(
                result = result ?: current.result,
                rendering = false,
                message = if (result == null) DocumentMessage.PAGE_FAILED else current.message,
                pages = current.pages.map { if (it.id == page.id && thumbnail != null) it.copy(thumbnail = thumbnail) else it },
            )
        }
    }
}
