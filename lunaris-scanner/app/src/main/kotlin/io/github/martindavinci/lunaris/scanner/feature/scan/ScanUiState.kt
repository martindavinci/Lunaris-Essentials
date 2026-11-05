package io.github.martindavinci.lunaris.scanner.feature.scan

import io.github.martindavinci.lunaris.scanner.model.ScannedCode

enum class CameraAccess { CHECKING, NEEDS_REQUEST, GRANTED, RATIONALE, PERMANENTLY_DENIED }

/** The system cannot tell "never asked" from "denied for good": both have no rationale. Only asking tells them apart. */
internal class CameraAccessCheck {
    private var asked = false

    fun asking() {
        asked = true
    }

    fun accessFor(granted: Boolean, showRationale: Boolean) = when {
        granted -> CameraAccess.GRANTED
        showRationale -> CameraAccess.RATIONALE
        asked -> CameraAccess.PERMANENTLY_DENIED
        else -> CameraAccess.NEEDS_REQUEST
    }
}

/** [choosing] is the first thing on the tab: the camera starts only once the user has said what it is for. */
data class ScanUiState(
    val choosing: Boolean = true,
    val cameraAccess: CameraAccess = CameraAccess.CHECKING,
    val cameraUnavailable: Boolean = false,
    val decodingImage: Boolean = false,
    val noCodeFound: Boolean = false,
)

/** Asked from outside the screen, by a tap on its tab or a shortcut. [id] tells a new request from one seen before. */
data class ScanRequest(val id: Int, val codes: Boolean)

sealed interface ScanAction {
    /** What the system says about the camera permission right now. */
    data class PermissionChecked(val granted: Boolean, val showRationale: Boolean) : ScanAction

    /** The system permission dialog is about to open. */
    data object PermissionRequested : ScanAction

    data class CodeDetected(val code: ScannedCode) : ScanAction

    data class ImagePicked(val uri: String) : ScanAction

    data object NoCodeMessageShown : ScanAction

    data object CodeChosen : ScanAction

    data object ChoiceAsked : ScanAction

    data class Requested(val request: ScanRequest) : ScanAction

    data object CameraUnavailable : ScanAction

    /** The screen went out of sight, for instance to open a link. A rotation does not count. */
    data object ScreenHidden : ScanAction

    data object ScreenShown : ScanAction

    /** The user moved on to another screen. A rotation does not count. */
    data object ScreenLeft : ScanAction
}

/** A code was filed and is ready to show. Sent once per code. */
data class ScanHit(val vibrate: Boolean)
