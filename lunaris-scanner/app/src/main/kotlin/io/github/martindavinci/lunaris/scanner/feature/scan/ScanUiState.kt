package io.github.martindavinci.lunaris.scanner.feature.scan

import io.github.martindavinci.lunaris.scanner.model.ScannedCode

/** What the Scan screen is for: reading codes, or photographing the pages of a document. */
enum class ScanMode { CODE, DOCUMENT }

enum class CameraAccess { CHECKING, NEEDS_REQUEST, GRANTED, RATIONALE, PERMANENTLY_DENIED }

data class ScanUiState(
    val cameraAccess: CameraAccess = CameraAccess.CHECKING,
    val cameraUnavailable: Boolean = false,
    val decodingImage: Boolean = false,
    val noCodeFound: Boolean = false,
    val mode: ScanMode = ScanMode.CODE,
    val documentPhotoFailed: Boolean = false,
)

sealed interface ScanAction {
    /** What the system says about the camera permission right now. */
    data class PermissionChecked(val granted: Boolean, val showRationale: Boolean) : ScanAction

    /** The system permission dialog is about to open. */
    data object PermissionRequested : ScanAction

    data class CodeDetected(val code: ScannedCode) : ScanAction

    data class ImagePicked(val uri: String) : ScanAction

    data object NoCodeMessageShown : ScanAction

    data class ModeSelected(val mode: ScanMode) : ScanAction

    data class DocumentImagePicked(val uri: String) : ScanAction

    data object ShutterPressed : ScanAction

    data class DocumentPhotoTaken(val path: String) : ScanAction

    data object DocumentPhotoFailed : ScanAction

    data object DocumentFailureShown : ScanAction

    data object CameraUnavailable : ScanAction

    /** The user moved on to another screen. A rotation does not count. */
    data object ScreenLeft : ScanAction
}

/** A code was filed and is ready to show. Sent once per code. */
data class ScanHit(val vibrate: Boolean)
