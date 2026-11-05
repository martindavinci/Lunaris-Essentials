package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.scanner.document.DocumentFiles
import io.github.martindavinci.lunaris.scanner.document.DocumentPhotos
import io.github.martindavinci.lunaris.scanner.document.ImportedPhoto
import io.github.martindavinci.lunaris.scanner.document.PageCorners
import io.github.martindavinci.lunaris.scanner.document.PageReading
import io.github.martindavinci.lunaris.scanner.document.PageRendering
import io.github.martindavinci.lunaris.scanner.document.PageSettings
import io.github.martindavinci.lunaris.scanner.feature.scan.CameraAccess
import io.github.martindavinci.lunaris.scanner.feature.scan.CameraAccessCheck
import io.github.martindavinci.lunaris.scanner.session.AwayTimer
import io.github.martindavinci.lunaris.scanner.session.MonotonicClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val photos: DocumentPhotos,
    files: DocumentFiles,
    rendering: PageRendering,
    reading: PageReading,
    clock: MonotonicClock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DocumentUiState())
    val state: StateFlow<DocumentUiState> = mutableState.asStateFlow()

    /** Until the user picks a clean-up, each page gets the one that suits it. From then on, theirs. */
    private var cleanChosen = false

    /** Leaving a camera opened from the review goes back there, and nothing is at risk. */
    private var cameFromReview = false
    private val display = PageDisplay(viewModelScope, photos, rendering, reading, mutableState)
    private val saving = DocumentSaving(viewModelScope, files, mutableState)
    private val pages = DocumentPages(photos, mutableState) { saving.savedSinceChange = false }
    private val access = CameraAccessCheck()
    private val away = AwayTimer(clock)

    fun onAction(action: DocumentAction) {
        when (action) {
            is DocumentAction.PhotoPicked -> import { photos.imported(action.uri) }
            is DocumentAction.PhotoTaken -> photoTaken(action.path)
            DocumentAction.OpenCamera -> {
                cameFromReview = true
                mutableState.update { it.copy(step = DocumentStep.CAMERA) }
            }
            DocumentAction.CameraFailed -> if (state.value.step == DocumentStep.CAMERA) say(DocumentMessage.CAMERA_FAILED)
            DocumentAction.CameraUnavailable -> mutableState.update { it.copy(cameraUnavailable = true) }
            is DocumentAction.CameraPermissionChecked -> mutableState.update {
                it.copy(cameraAccess = access.accessFor(action.granted, action.showRationale))
            }
            DocumentAction.CameraPermissionRequested -> {
                access.asking()
                mutableState.update { it.copy(cameraAccess = CameraAccess.CHECKING) }
            }
            DocumentAction.ScreenHidden -> away.hidden()
            DocumentAction.ScreenShown -> if (away.cameBackLate() && state.value.pages.isEmpty()) close()
            DocumentAction.BackPressed -> back()
            is DocumentAction.CornerMoved -> mutableState.update { it.copy(corners = it.corners.moved(action.index, action.to)) }
            DocumentAction.CornersReset -> mutableState.update { it.copy(corners = it.currentPage?.found ?: PageCorners.START) }
            DocumentAction.CornersDone -> confirmCorners()
            DocumentAction.AnotherPage -> anotherPage()
            DocumentAction.RetakePhoto -> retake()
            DocumentAction.FinishPages -> toReview()
            DocumentAction.EditCorners -> mutableState.update {
                it.copy(step = DocumentStep.CORNERS, newPage = false, corners = it.currentPage?.settings?.corners ?: PageCorners.START)
            }
            is DocumentAction.CleanChosen -> {
                cleanChosen = true
                changePage { it.copy(clean = action.mode) }
            }
            is DocumentAction.PaperChosen -> changePage { it.copy(paper = action.paper) }
            DocumentAction.TurnLeft -> changePage { it.turnedLeft() }
            DocumentAction.TurnRight -> changePage { it.turnedRight() }
            is DocumentAction.PageSelected -> showPage(action.index)
            DocumentAction.MovePageEarlier -> pages.moveCurrent(-1)
            DocumentAction.MovePageLater -> pages.moveCurrent(1)
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

    override fun onCleared() = state.value.pages.forEach { photos.delete(it.photoPath) }

    /** The scan is safe in a file: the next one starts on the camera. */
    private fun startOver() {
        cameFromReview = false
        pages.clear()
        cleanChosen = false
    }

    private fun close() = mutableState.update { it.copy(closed = true) }

    private fun say(message: DocumentMessage) = mutableState.update { it.copy(message = message) }

    /** The shutter may have been pressed just before leaving the camera: that photo is not wanted any more. */
    private fun photoTaken(path: String) {
        val now = state.value
        if (now.step == DocumentStep.CAMERA && !now.closed) import { photos.captured(path) } else photos.delete(path)
    }

    private fun import(read: suspend () -> ImportedPhoto?) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val photo = read()
            mutableState.update { it.copy(busy = false) }
            if (photo != null) {
                pages.add(photo, keepClean = cleanChosen)
                display.loadPhoto(renderAfter = false, findPage = true)
            } else {
                say(DocumentMessage.PHOTO_FAILED)
            }
        }
    }

    /** While a file is being written leaving would cut it short, and the pages it is made from would go. */
    private fun back() {
        val now = state.value
        if (now.busy) return
        when (now.step) {
            DocumentStep.CAMERA -> when {
                now.pages.isEmpty() -> close()
                cameFromReview -> toReview()
                else -> mutableState.update { it.copy(confirm = DocumentConfirm.DISCARD) }
            }
            DocumentStep.CORNERS -> if (now.newPage) retake() else mutableState.update { it.copy(step = DocumentStep.REVIEW) }
            DocumentStep.REVIEW -> if (saving.savedSinceChange) startOver() else mutableState.update { it.copy(confirm = DocumentConfirm.DISCARD) }
        }
    }

    private fun confirmed() {
        val confirm = state.value.confirm
        mutableState.update { it.copy(confirm = null) }
        when (confirm) {
            DocumentConfirm.DELETE_PAGE -> deleteCurrentPage()
            DocumentConfirm.DISCARD -> close()
            null -> Unit
        }
    }

    /** Back on the camera, for this page again. */
    private fun retake() {
        display.cancel()
        pages.dropCurrent()
        forgetCleanIfNoPagesLeft()
        mutableState.update { it.copy(step = DocumentStep.CAMERA, newPage = false, photo = null, result = null) }
    }

    /** With no page left the next one is the start of a scan. */
    private fun forgetCleanIfNoPagesLeft() {
        if (state.value.pages.isEmpty()) cleanChosen = false
    }

    private fun deleteCurrentPage() {
        display.cancel()
        pages.dropCurrent()
        forgetCleanIfNoPagesLeft()
        if (state.value.pages.isEmpty()) {
            mutableState.update { it.copy(step = DocumentStep.CAMERA, photo = null, result = null) }
        } else {
            mutableState.update { it.copy(step = DocumentStep.REVIEW, result = null) }
            display.loadPhoto(renderAfter = true)
        }
    }

    private fun toReview() {
        mutableState.update { it.copy(step = DocumentStep.REVIEW, newPage = false, current = it.current.coerceAtMost(it.pages.size - 1), result = null) }
        display.loadPhoto(renderAfter = true)
    }

    private fun confirmCorners() {
        val now = state.value
        if (now.cornersProblem != null || now.photo == null) return
        val suggest = now.newPage && !cleanChosen
        pages.changeCurrent { it.copy(corners = now.corners) }
        mutableState.update { it.copy(step = DocumentStep.REVIEW, newPage = false) }
        display.render(suggestClean = suggest)
    }

    private fun anotherPage() {
        val now = state.value
        if (now.cornersProblem != null || now.photo == null) return
        val suggest = now.newPage && !cleanChosen
        pages.changeCurrent { it.copy(corners = now.corners) }
        display.prepareInBackground(suggest) { !cleanChosen }
        mutableState.update { it.copy(step = DocumentStep.CAMERA, newPage = false) }
    }

    private fun changePage(change: (PageSettings) -> PageSettings) {
        pages.changeCurrent(change)
        display.render()
    }

    private fun showPage(index: Int) {
        if (index == state.value.current || index !in state.value.pages.indices) return
        mutableState.update { it.copy(current = index, result = null) }
        display.loadPhoto(renderAfter = true)
    }

}
