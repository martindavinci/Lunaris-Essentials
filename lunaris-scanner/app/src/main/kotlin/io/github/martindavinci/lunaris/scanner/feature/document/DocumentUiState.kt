package io.github.martindavinci.lunaris.scanner.feature.document

import io.github.martindavinci.lunaris.scanner.document.CleanMode
import io.github.martindavinci.lunaris.scanner.document.CornersProblem
import io.github.martindavinci.lunaris.scanner.document.DocumentPage
import io.github.martindavinci.lunaris.scanner.document.PageCorners
import io.github.martindavinci.lunaris.scanner.document.PagePixels
import io.github.martindavinci.lunaris.scanner.document.PagePoint
import io.github.martindavinci.lunaris.scanner.document.PaperSize
import io.github.martindavinci.lunaris.scanner.document.PictureFormat
import io.github.martindavinci.lunaris.scanner.feature.scan.CameraAccess

enum class DocumentStep { CAMERA, CORNERS, REVIEW }

enum class DocumentMessage { PHOTO_FAILED, CAMERA_FAILED, PAGE_FAILED, SAVED, SAVE_FAILED, SHARE_FAILED }

enum class DocumentConfirm { DELETE_PAGE, DISCARD }

/**
 * [photo] is a screen-sized copy of the current page's photo, [corners] the ones being moved, [result]
 * the current page as it will come out. [busy] covers reading a photo and writing files. [newPage] is a page
 * just taken, whose corners are being set for the first time.
 */
data class DocumentUiState(
    val step: DocumentStep = DocumentStep.CAMERA,
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
    val newPage: Boolean = false,
    val cameraAccess: CameraAccess = CameraAccess.CHECKING,
    val cameraUnavailable: Boolean = false,
) {
    val currentPage: DocumentPage? get() = pages.getOrNull(current)
    val cornersProblem: CornersProblem? get() = corners.problem()

    /** Nothing was found on the photo, and the user has not started on the corners yet. */
    val cornersNotFound: Boolean get() = photo != null && currentPage?.found == null && corners == PageCorners.START
}

sealed interface DocumentAction {
    data class PhotoPicked(val uri: String) : DocumentAction

    data class PhotoTaken(val path: String) : DocumentAction

    data object OpenCamera : DocumentAction

    data object CameraFailed : DocumentAction

    data object CameraUnavailable : DocumentAction

    data class CameraPermissionChecked(val granted: Boolean, val showRationale: Boolean) : DocumentAction

    data object CameraPermissionRequested : DocumentAction

    data object ScreenHidden : DocumentAction

    data object ScreenShown : DocumentAction

    data object AnotherPage : DocumentAction

    data object RetakePhoto : DocumentAction

    data object FinishPages : DocumentAction

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
