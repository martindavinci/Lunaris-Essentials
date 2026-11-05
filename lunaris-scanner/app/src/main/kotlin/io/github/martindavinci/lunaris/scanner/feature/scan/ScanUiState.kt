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

/** After each reading the card stays still for this long, so that nothing changes under a finger on its way to it. */
internal const val READ_PAUSE_MILLIS = 2_000L

/**
 * [choosing] is the first thing on the tab: the camera starts only once the user has said what it is for.
 * [session] holds what was read since, the newest first, and [readings] tells one reading from the next.
 */
data class ScanUiState(
    val choosing: Boolean = true,
    val cameraAccess: CameraAccess = CameraAccess.CHECKING,
    val cameraUnavailable: Boolean = false,
    val decodingImage: Boolean = false,
    val noCodeFound: Boolean = false,
    val session: List<SessionCode> = emptyList(),
    val readings: Int = 0,
    val pauseRunning: Boolean = false,
    val cardHeld: Boolean = false,
    val listOpen: Boolean = false,
) {
    val readingPaused: Boolean get() = pauseRunning || cardHeld || listOpen
}

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

    data class DetailsAsked(val code: ScannedCode) : ScanAction

    data class CardHeld(val held: Boolean) : ScanAction

    data object ListOpened : ScanAction

    data object ListClosed : ScanAction

    data object CameraUnavailable : ScanAction

    /** The screen went out of sight, for instance to open a link. A rotation does not count. */
    data object ScreenHidden : ScanAction

    data object ScreenShown : ScanAction

    /** The user moved on to another screen. A rotation does not count. */
    data object ScreenLeft : ScanAction
}

/** A code was read and filed. Sent once per reading. */
data class ScanHit(val vibrate: Boolean)
