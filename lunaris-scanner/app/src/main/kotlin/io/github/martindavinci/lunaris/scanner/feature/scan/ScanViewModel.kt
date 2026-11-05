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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val clock: MonotonicClock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = mutableState.asStateFlow()

    private val hitChannel = Channel<ScanHit>(Channel.BUFFERED)
    val hits: Flow<ScanHit> = hitChannel.receiveAsFlow()

    /** Kept here so that it remembers the last code across rotations and trips to the Result screen. */
    val analyzer = CameraAnalyzer(decoder, clock::millis) { onAction(ScanAction.CodeDetected(it)) }

    /** The parent of whatever may end in a hit, so that leaving the screen can stop it all. */
    private val pendingHits = SupervisorJob(viewModelScope.coroutineContext.job)

    private val access = CameraAccessCheck()
    private val away = AwayTimer(clock)
    private var lastRequest = 0
    private var pause: Job? = null

    fun onAction(action: ScanAction) {
        when (action) {
            is ScanAction.PermissionChecked -> mutableState.update {
                it.copy(cameraAccess = access.accessFor(action.granted, action.showRationale))
            }
            ScanAction.PermissionRequested -> {
                access.asking()
                mutableState.update { it.copy(cameraAccess = CameraAccess.CHECKING) }
            }
            is ScanAction.CodeDetected -> viewModelScope.launch(pendingHits) { cameraHit(action.code) }
            is ScanAction.ImagePicked -> readImage(action.uri)
            ScanAction.NoCodeMessageShown -> mutableState.update { it.copy(noCodeFound = false) }
            ScanAction.CodeChosen -> mutableState.update { it.copy(choosing = false) }
            ScanAction.ChoiceAsked -> backToChoice()
            is ScanAction.DetailsAsked -> currentResult.set(action.code)
            is ScanAction.CardHeld -> mutableState.update { it.copy(cardHeld = action.held) }
            ScanAction.ListOpened -> mutableState.update { it.copy(listOpen = true) }
            ScanAction.ListClosed -> mutableState.update { it.copy(listOpen = false) }
            is ScanAction.Requested -> requested(action.request)
            ScanAction.ScreenHidden -> away.hidden()
            ScanAction.ScreenShown -> if (away.cameBackLate()) backToChoice()
            ScanAction.CameraUnavailable -> mutableState.update { it.copy(cameraUnavailable = true) }
            ScanAction.ScreenLeft -> {
                dropPendingHits()
                away.forget()
                endPause()
            }
        }
    }

    /** The same request comes back whenever the screen does: it counts only the first time. */
    private fun requested(request: ScanRequest) {
        if (request.id == lastRequest) return
        lastRequest = request.id
        backToChoice()
        mutableState.update { it.copy(choosing = !request.codes) }
    }

    /** What was read belongs to this round at the camera: it stays in the history, if that is on. */
    private fun backToChoice() {
        endPause()
        mutableState.update { it.copy(choosing = true, session = emptyList(), listOpen = false) }
    }

    private fun endPause() {
        pause?.cancel()
        mutableState.update { it.copy(pauseRunning = false, cardHeld = false) }
    }

    private fun readImage(uri: String) {
        mutableState.update { it.copy(decodingImage = true, noCodeFound = false) }
        viewModelScope.launch(pendingHits) {
            val code = imageDecoder.decode(uri)
            if (code != null) fileHit(code)
            mutableState.update { it.copy(decodingImage = false, noCodeFound = code == null) }
        }
    }

    /** A code arriving just as a finger comes down must not change what that finger is about to open. It can wait. */
    private suspend fun cameraHit(code: ScannedCode) {
        val now = state.value
        if (now.cardHeld || now.listOpen) analyzer.forget(code) else fileHit(code)
    }

    private suspend fun fileHit(code: ScannedCode) {
        val isNew = state.value.session.none { it.code == code }
        mutableState.update { state ->
            state.copy(
                session = listOf(SessionCode(code, System.currentTimeMillis())) + state.session.filter { it.code != code },
                readings = state.readings + 1,
                pauseRunning = true,
            )
        }
        pause?.cancel()
        pause = viewModelScope.launch {
            delay(READ_PAUSE_MILLIS)
            mutableState.update { it.copy(pauseRunning = false) }
        }
        if (isNew) scanRecorder.record(code)
        hitChannel.send(ScanHit(settingsStore.settings.first().vibrateOnScan))
    }

    /** A reading that nobody is there to see would only vibrate out of the blue. */
    private fun dropPendingHits() {
        pendingHits.cancelChildren()
        while (hitChannel.tryReceive().isSuccess) Unit
        mutableState.update { it.copy(decodingImage = false) }
    }
}
