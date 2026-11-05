package io.github.martindavinci.lunaris.scanner.feature.document

import io.github.martindavinci.lunaris.scanner.document.CleanMode
import io.github.martindavinci.lunaris.scanner.document.CornersProblem
import io.github.martindavinci.lunaris.scanner.document.DocumentPage
import io.github.martindavinci.lunaris.scanner.document.PageCorners
import io.github.martindavinci.lunaris.scanner.document.PagePixels
import io.github.martindavinci.lunaris.scanner.document.PagePoint
import io.github.martindavinci.lunaris.scanner.document.PaperSize
import io.github.martindavinci.lunaris.scanner.document.PictureFormat

enum class DocumentStep { CAMERA, CORNERS, REVIEW }

enum class DocumentMessage { PHOTO_FAILED, CAMERA_FAILED, PAGE_FAILED, SAVED, SAVE_FAILED, SHARE_FAILED }

enum class DocumentConfirm { DELETE_PAGE, DISCARD }

/**
 * [photo] is a screen-sized copy of the current page's photo, [corners] the ones being moved, [result]
 * the current page as it will come out. [busy] covers reading a photo and writing files.
 */
data class DocumentUiState(
    val step: DocumentStep = DocumentStep.CORNERS,
    val pages: List<DocumentPage> = emptyList(),
    val current: Int = 0,
    val photo: PagePixels? = null,
    val corners: PageCorners = PageCorners.START,
    val result: PagePixels? = null,
    val rendering: Boolean = false,
    val busy: Boolean = false,
    val message: DocumentMessage? = null,
    val confirm: DocumentConfirm? = null,
    val shareUri: String? = null,
    val closed: Boolean = false,
) {
    val currentPage: DocumentPage? get() = pages.getOrNull(current)
    val cornersProblem: CornersProblem? get() = corners.problem()
}

sealed interface DocumentAction {
    data class PhotoPicked(val uri: String) : DocumentAction

    data class PhotoTaken(val path: String) : DocumentAction

    data object OpenCamera : DocumentAction

    data object CameraFailed : DocumentAction

    data object CameraRefused : DocumentAction

    data object BackPressed : DocumentAction

    data class CornerMoved(val index: Int, val to: PagePoint) : DocumentAction

    data object CornersReset : DocumentAction

    data object CornersDone : DocumentAction

    data object EditCorners : DocumentAction

    data class CleanChosen(val mode: CleanMode) : DocumentAction

    data class PaperChosen(val paper: PaperSize) : DocumentAction

    data object TurnLeft : DocumentAction

    data object TurnRight : DocumentAction

    data class PageSelected(val index: Int) : DocumentAction

    data object MovePageEarlier : DocumentAction

    data object MovePageLater : DocumentAction

    data object DeletePage : DocumentAction

    data object Confirmed : DocumentAction

    data object Dismissed : DocumentAction

    data class SavePdfTo(val uri: String) : DocumentAction

    data class SavePictureTo(val uri: String, val format: PictureFormat) : DocumentAction

    data class SavePicturesTo(val folderUri: String, val format: PictureFormat) : DocumentAction

    data object SharePdf : DocumentAction

    data object ShareHandled : DocumentAction

    data object MessageShown : DocumentAction
}
