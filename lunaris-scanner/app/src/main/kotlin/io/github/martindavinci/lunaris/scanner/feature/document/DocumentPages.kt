package io.github.martindavinci.lunaris.scanner.feature.document

import io.github.martindavinci.lunaris.scanner.document.DocumentPage
import io.github.martindavinci.lunaris.scanner.document.DocumentPhotos
import io.github.martindavinci.lunaris.scanner.document.ImportedPhoto
import io.github.martindavinci.lunaris.scanner.document.PageCorners
import io.github.martindavinci.lunaris.scanner.document.PageSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The pages of the scan under way, with their photos. [onChanged] hears of whatever makes a saved file out of date. */
internal class DocumentPages(
    private val photos: DocumentPhotos,
    private val state: MutableStateFlow<DocumentUiState>,
    private val onChanged: () -> Unit,
) {
    private var nextId = 0L

    /** A page in a run usually wants the paper of the last one, and its clean-up if chosen, but its photo is framed anew. */
    fun add(photo: ImportedPhoto, keepClean: Boolean) {
        val last = state.value.pages.lastOrNull()?.settings ?: PageSettings()
        val page = DocumentPage(
            id = nextId++,
            photoPath = photo.path,
            workSize = photo.size,
            settings = if (keepClean) PageSettings(clean = last.clean, paper = last.paper) else PageSettings(paper = last.paper),
        )
        onChanged()
        state.update {
            it.copy(
                step = DocumentStep.CORNERS,
                newPage = true,
                pages = it.pages + page,
                current = it.pages.size,
                corners = PageCorners.START,
                result = null,
            )
        }
    }

    fun changeCurrent(change: (PageSettings) -> PageSettings) {
        onChanged()
        state.update { now ->
            now.copy(pages = now.pages.mapIndexed { index, page -> if (index == now.current) page.copy(settings = change(page.settings)) else page })
        }
    }

    fun dropCurrent() {
        val now = state.value
        val page = now.currentPage ?: return
        photos.delete(page.photoPath)
        onChanged()
        val left = now.pages - page
        state.update { it.copy(pages = left, current = now.current.coerceIn(0, (left.size - 1).coerceAtLeast(0))) }
    }

    fun moveCurrent(by: Int) {
        val now = state.value
        val target = now.current + by
        if (target !in now.pages.indices) return
        onChanged()
        val reordered = now.pages.toMutableList().apply { add(target, removeAt(now.current)) }
        state.update { it.copy(pages = reordered, current = target) }
    }

    fun clear() {
        state.value.pages.forEach { photos.delete(it.photoPath) }
        onChanged()
        state.update { it.copy(step = DocumentStep.CAMERA, pages = emptyList(), current = 0, photo = null, result = null, newPage = false) }
    }
}
