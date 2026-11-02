package io.github.martindavinci.lunaris.scanner.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(private val currentResult: CurrentResult) : ViewModel() {
    // Tied to the code, so that a code arriving while this screen is open starts masked.
    private val revealedCode = MutableStateFlow<ScannedCode?>(null)

    val state: StateFlow<ResultUiState> = combine(currentResult.code, revealedCode) { code, revealed ->
        resultUiState(code, secretRevealed = code != null && code == revealed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, resultUiState(currentResult.code.value, secretRevealed = false))

    fun onAction(action: ResultUiAction) {
        when (action) {
            ResultUiAction.ToggleSecret -> {
                val code = currentResult.code.value
                revealedCode.value = if (revealedCode.value == code) null else code
            }
        }
    }
}
