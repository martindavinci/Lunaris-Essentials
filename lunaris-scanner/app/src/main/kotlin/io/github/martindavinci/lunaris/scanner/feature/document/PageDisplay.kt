package io.github.martindavinci.lunaris.scanner.feature.document

import io.github.martindavinci.lunaris.scanner.document.CleanMode
import io.github.martindavinci.lunaris.scanner.document.DocumentPhotos
import io.github.martindavinci.lunaris.scanner.document.PageReading
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
    private val reading: PageReading,
    private val state: MutableStateFlow<DocumentUiState>,
) {
    private var job: Job? = null

    /** The page on its way is not wanted any more. */
    fun cancel() {
        job?.cancel()
    }

    /** With [findPage] the corners are set with the photo, so that the user never sees them jump. */
    fun loadPhoto(renderAfter: Boolean, findPage: Boolean = false) {
        val page = state.value.currentPage ?: return
        job?.cancel()
        state.update { it.copy(photo = null, rendering = renderAfter) }
        job = scope.launch {
            val photo = photos.load(page.photoPath, PHOTO_LONG_SIDE)
            val found = if (findPage && photo != null) reading.find(photo) else null
            state.update { now ->
                now.copy(
                    photo = photo,
                    rendering = false,
                    message = if (photo == null) DocumentMessage.PAGE_FAILED else now.message,
                    corners = found ?: now.corners,
                    pages = now.pages.map { if (it.id == page.id) it.copy(found = found ?: it.found) else it },
                )
            }
            if (renderAfter && photo != null) renderNow()
        }
    }

    /**
     * With the photo still on its way, it has to arrive first: stopping it would leave nothing to render.
     * With [suggestClean] the page first gets the clean-up that suits it.
     */
    fun render(suggestClean: Boolean = false) {
        if (state.value.photo == null) {
            loadPhoto(renderAfter = true)
            return
        }
        job?.cancel()
        job = scope.launch {
            if (suggestClean) applySuggestedClean()
            renderNow()
        }
    }

    /**
     * For a page left behind while the next one is taken: its clean-up and its thumbnail, on their own, so that
     * nothing shown afterwards stops them. [stillSuggesting] may turn false in the meantime.
     */
    fun prepareInBackground(suggestClean: Boolean, stillSuggesting: () -> Boolean) {
        val now = state.value
        val page = now.currentPage ?: return
        val photo = now.photo ?: return
        scope.launch {
            if (suggestClean) {
                val clean = reading.suggestClean(photo, page.settings.corners)
                if (stillSuggesting()) setClean(page.id, clean)
            }
            val settings = state.value.pages.firstOrNull { it.id == page.id }?.settings ?: return@launch
            val thumbnail = rendering.render(photo, settings, page.workSize, THUMBNAIL_LONG_SIDE) ?: return@launch
            state.update { current ->
                current.copy(pages = current.pages.map { if (it.id == page.id) it.copy(thumbnail = thumbnail) else it })
            }
        }
    }

    /** If that page is on show by now, it was rendered with the clean-up it had before. */
    private fun setClean(pageId: Long, clean: CleanMode) {
        state.update { current ->
            current.copy(pages = current.pages.map { if (it.id == pageId) it.copy(settings = it.settings.copy(clean = clean)) else it })
        }
        if (state.value.step == DocumentStep.REVIEW && state.value.currentPage?.id == pageId) render()
    }

    private suspend fun applySuggestedClean() {
        val now = state.value
        val page = now.currentPage ?: return
        val photo = now.photo ?: return
        state.update { it.copy(rendering = true) }
        val clean = reading.suggestClean(photo, page.settings.corners)
        state.update { current ->
            current.copy(pages = current.pages.map { if (it.id == page.id) it.copy(settings = it.settings.copy(clean = clean)) else it })
        }
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
