package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.scanner.document.DocumentFiles
import io.github.martindavinci.lunaris.scanner.document.DocumentInbox
import io.github.martindavinci.lunaris.scanner.document.DocumentPage
import io.github.martindavinci.lunaris.scanner.document.DocumentPhotos
import io.github.martindavinci.lunaris.scanner.document.ImportedPhoto
import io.github.martindavinci.lunaris.scanner.document.PageCorners
import io.github.martindavinci.lunaris.scanner.document.PageReading
import io.github.martindavinci.lunaris.scanner.document.PageRendering
import io.github.martindavinci.lunaris.scanner.document.PageSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentViewModel @Inject constructor(
    inbox: DocumentInbox,
    private val photos: DocumentPhotos,
    files: DocumentFiles,
    rendering: PageRendering,
    reading: PageReading,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DocumentUiState())
    val state: StateFlow<DocumentUiState> = mutableState.asStateFlow()

    private var nextPageId = 0L
    private var editingNewPage = false

    /** Until the user picks a clean-up, each page gets the one that suits it. From then on, theirs. */
    private var cleanChosen = false
    private val display = PageDisplay(viewModelScope, photos, rendering, reading, mutableState)
    private val saving = DocumentSaving(viewModelScope, files, mutableState)

    init {
        // Nothing there when the process was lost: the scan went with it.
        val first = inbox.take()
        if (first != null) addPage(first) else close()
    }

    fun onAction(action: DocumentAction) {
        when (action) {
            is DocumentAction.PhotoPicked -> import { photos.imported(action.uri) }
            is DocumentAction.PhotoTaken -> photoTaken(action.path)
            DocumentAction.OpenCamera -> mutableState.update { it.copy(step = DocumentStep.CAMERA) }
            DocumentAction.CameraFailed -> if (state.value.step == DocumentStep.CAMERA) leaveCamera(DocumentMessage.CAMERA_FAILED)
            DocumentAction.CameraRefused -> leaveCamera(DocumentMessage.CAMERA_FAILED)
            DocumentAction.BackPressed -> back()
            is DocumentAction.CornerMoved -> mutableState.update { it.copy(corners = it.corners.moved(action.index, action.to)) }
            DocumentAction.CornersReset -> mutableState.update { it.copy(corners = it.currentPage?.found ?: PageCorners.START) }
            DocumentAction.CornersDone -> confirmCorners()
            DocumentAction.EditCorners -> mutableState.update {
                it.copy(step = DocumentStep.CORNERS, corners = it.currentPage?.settings?.corners ?: PageCorners.START)
            }
            is DocumentAction.CleanChosen -> {
                cleanChosen = true
                changePage { it.copy(clean = action.mode) }
            }
            is DocumentAction.PaperChosen -> changePage { it.copy(paper = action.paper) }
            DocumentAction.TurnLeft -> changePage { it.turnedLeft() }
            DocumentAction.TurnRight -> changePage { it.turnedRight() }
            is DocumentAction.PageSelected -> showPage(action.index)
            DocumentAction.MovePageEarlier -> movePage(-1)
            DocumentAction.MovePageLater -> movePage(1)
            DocumentAction.DeletePage -> mutableState.update { it.copy(confirm = DocumentConfirm.DELETE_PAGE) }
            DocumentAction.Confirmed -> confirmed()
            DocumentAction.Dismissed -> mutableState.update { it.copy(confirm = null) }
            is DocumentAction.SavePdfTo -> saving.savePdf(action.uri)
            is DocumentAction.SavePictureTo -> saving.savePicture(action.uri, action.format)
            is DocumentAction.SavePicturesTo -> saving.savePictures(action.folderUri, action.format)
            DocumentAction.SharePdf -> saving.share()
            DocumentAction.ShareHandled -> mutableState.update { it.copy(shareUri = null) }
            DocumentAction.MessageShown -> mutableState.update { it.copy(message = null) }
        }
    }

    /** Only this scan's own: the next one may have begun while this screen was still leaving. */
    override fun onCleared() = state.value.pages.forEach { photos.delete(it.photoPath) }

    private fun close() = mutableState.update { it.copy(closed = true) }

    /** The shutter may have been pressed just before leaving the camera: that photo is not wanted any more. */
    private fun photoTaken(path: String) {
        if (state.value.step == DocumentStep.CAMERA) import { photos.captured(path) } else photos.delete(path)
    }

    private fun import(read: suspend () -> ImportedPhoto?) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val photo = read()
            mutableState.update { it.copy(busy = false) }
            if (photo != null) addPage(photo) else leaveCamera(DocumentMessage.PHOTO_FAILED)
        }
    }

    private fun leaveCamera(message: DocumentMessage) {
        mutableState.update { it.copy(step = if (it.step == DocumentStep.CAMERA) DocumentStep.REVIEW else it.step, message = message) }
    }

    /** A page in a run usually wants the paper of the last one, and its clean-up if chosen, but its photo is framed anew. */
    private fun addPage(photo: ImportedPhoto) {
        val last = state.value.pages.lastOrNull()?.settings ?: PageSettings()
        val page = DocumentPage(
            id = nextPageId++,
            photoPath = photo.path,
            workSize = photo.size,
            settings = if (cleanChosen) PageSettings(clean = last.clean, paper = last.paper) else PageSettings(paper = last.paper),
        )
        editingNewPage = true
        saving.savedSinceChange = false
        mutableState.update {
            it.copy(
                step = DocumentStep.CORNERS,
                pages = it.pages + page,
                current = it.pages.size,
                corners = PageCorners.START,
                result = null,
            )
        }
        display.loadPhoto(renderAfter = false, findPage = true)
    }

    /** While a file is being written leaving would cut it short, and the pages it is made from would go. */
    private fun back() {
        val now = state.value
        if (now.busy) return
        when (now.step) {
            DocumentStep.CAMERA -> mutableState.update { it.copy(step = DocumentStep.REVIEW) }
            DocumentStep.CORNERS -> if (editingNewPage) dropCurrentPage() else mutableState.update { it.copy(step = DocumentStep.REVIEW) }
            DocumentStep.REVIEW -> if (saving.savedSinceChange) close() else mutableState.update { it.copy(confirm = DocumentConfirm.DISCARD) }
        }
    }

    private fun confirmed() {
        val confirm = state.value.confirm
        mutableState.update { it.copy(confirm = null) }
        when (confirm) {
            DocumentConfirm.DELETE_PAGE -> dropCurrentPage()
            DocumentConfirm.DISCARD -> close()
            null -> Unit
        }
    }

    private fun dropCurrentPage() {
        val now = state.value
        val page = now.currentPage ?: return
        photos.delete(page.photoPath)
        editingNewPage = false
        saving.savedSinceChange = false
        val left = now.pages - page
        if (left.isEmpty()) {
            mutableState.update { it.copy(pages = left) }
            close()
            return
        }
        mutableState.update { it.copy(step = DocumentStep.REVIEW, pages = left, current = now.current.coerceAtMost(left.size - 1), result = null) }
        display.loadPhoto(renderAfter = true)
    }

    private fun confirmCorners() {
        val now = state.value
        if (now.cornersProblem != null || now.photo == null) return
        val suggest = editingNewPage && !cleanChosen
        editingNewPage = false
        replaceCurrentPage { it.copy(settings = it.settings.copy(corners = now.corners)) }
        mutableState.update { it.copy(step = DocumentStep.REVIEW) }
        display.render(suggestClean = suggest)
    }

    private fun changePage(change: (PageSettings) -> PageSettings) {
        replaceCurrentPage { it.copy(settings = change(it.settings)) }
        display.render()
    }

    private fun replaceCurrentPage(change: (DocumentPage) -> DocumentPage) {
        saving.savedSinceChange = false
        mutableState.update { now ->
            now.copy(pages = now.pages.mapIndexed { index, page -> if (index == now.current) change(page) else page })
        }
    }

    private fun showPage(index: Int) {
        if (index == state.value.current || index !in state.value.pages.indices) return
        mutableState.update { it.copy(current = index, result = null) }
        display.loadPhoto(renderAfter = true)
    }

    private fun movePage(by: Int) {
        val now = state.value
        val target = now.current + by
        if (target !in now.pages.indices) return
        saving.savedSinceChange = false
        val reordered = now.pages.toMutableList().apply { add(target, removeAt(now.current)) }
        mutableState.update { it.copy(pages = reordered, current = target) }
    }
}
