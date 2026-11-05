package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.scan.CameraAnalyzer
import io.github.martindavinci.lunaris.scanner.scan.CodeDecoder
import io.github.martindavinci.lunaris.scanner.scan.ImageDecoder
import io.github.martindavinci.lunaris.scanner.session.AwayTimer
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import io.github.martindavinci.lunaris.scanner.session.MonotonicClock
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import io.github.martindavinci.lunaris.scanner.share.ScanRecorder
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    decoder: CodeDecoder,
    private val imageDecoder: ImageDecoder,
    private val scanRecorder: ScanRecorder,
    private val currentResult: CurrentResult,
    private val settingsStore: SettingsStore,
    clock: MonotonicClock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = mutableState.asStateFlow()

    private val hitChannel = Channel<ScanHit>(Channel.BUFFERED)
    val hits: Flow<ScanHit> = hitChannel.receiveAsFlow()

    /** Kept here so that it remembers the last code across rotations and trips to the Result screen. */
    val analyzer = CameraAnalyzer(decoder) { onAction(ScanAction.CodeDetected(it)) }

    /** The parent of whatever may end in a hit, so that leaving the screen can stop it all. */
    private val pendingHits = SupervisorJob(viewModelScope.coroutineContext.job)

    private val access = CameraAccessCheck()
    private val away = AwayTimer(clock)
    private var lastRequest = 0

    fun onAction(action: ScanAction) {
        when (action) {
            is ScanAction.PermissionChecked -> mutableState.update {
                it.copy(cameraAccess = access.accessFor(action.granted, action.showRationale))
            }
            ScanAction.PermissionRequested -> {
                access.asking()
                mutableState.update { it.copy(cameraAccess = CameraAccess.CHECKING) }
            }
            is ScanAction.CodeDetected -> viewModelScope.launch(pendingHits) { fileHit(action.code) }
            is ScanAction.ImagePicked -> readImage(action.uri)
            ScanAction.NoCodeMessageShown -> mutableState.update { it.copy(noCodeFound = false) }
            ScanAction.CodeChosen -> mutableState.update { it.copy(choosing = false) }
            ScanAction.ChoiceAsked -> mutableState.update { it.copy(choosing = true) }
            is ScanAction.Requested -> requested(action.request)
            ScanAction.ScreenHidden -> away.hidden()
            ScanAction.ScreenShown -> if (away.cameBackLate()) mutableState.update { it.copy(choosing = true) }
            ScanAction.CameraUnavailable -> mutableState.update { it.copy(cameraUnavailable = true) }
            ScanAction.ScreenLeft -> {
                dropPendingHits()
                away.forget()
            }
        }
    }

    /** The same request comes back whenever the screen does: it counts only the first time. */
    private fun requested(request: ScanRequest) {
        if (request.id == lastRequest) return
        lastRequest = request.id
        mutableState.update { it.copy(choosing = !request.codes) }
    }

    private fun readImage(uri: String) {
        mutableState.update { it.copy(decodingImage = true, noCodeFound = false) }
        viewModelScope.launch(pendingHits) {
            val code = imageDecoder.decode(uri)
            if (code != null) fileHit(code)
            mutableState.update { it.copy(decodingImage = false, noCodeFound = code == null) }
        }
    }

    private suspend fun fileHit(code: ScannedCode) {
        scanRecorder.record(code)
        val vibrate = settingsStore.settings.first().vibrateOnScan
        currentResult.set(code)
        hitChannel.send(ScanHit(vibrate))
    }

    /** A hit that nobody is there to receive would replace the result of another screen, or open late. */
    private fun dropPendingHits() {
        pendingHits.cancelChildren()
        while (hitChannel.tryReceive().isSuccess) Unit
        mutableState.update { it.copy(decodingImage = false) }
    }
}
