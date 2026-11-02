package io.github.martindavinci.lunaris.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.martindavinci.lunaris.scanner.scan.ImageDecoder
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import io.github.martindavinci.lunaris.scanner.share.ScanRecorder
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsStore: SettingsStore,
    private val currentResult: CurrentResult,
    private val imageDecoder: ImageDecoder,
    private val scanRecorder: ScanRecorder,
) : ViewModel() {

    private val sharedImage = MutableStateFlow(SharedImageState.NONE)

    val state: StateFlow<MainUiState> =
        combine(settingsStore.settings, sharedImage) { settings, image -> MainUiState(settings, image) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MainUiState())

    fun onAction(action: MainAction) {
        when (action) {
            is MainAction.ImageShared -> readSharedImage(action.uri)
        }
    }

    private fun readSharedImage(uri: String) {
        sharedImage.value = SharedImageState.DECODING
        viewModelScope.launch {
            val code = imageDecoder.decode(uri)
            if (code == null) {
                sharedImage.value = SharedImageState.NOT_FOUND
            } else {
                scanRecorder.record(code)
                currentResult.set(code)
                sharedImage.value = SharedImageState.FOUND
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
