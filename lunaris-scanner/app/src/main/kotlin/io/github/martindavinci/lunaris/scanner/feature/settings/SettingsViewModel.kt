package io.github.martindavinci.lunaris.scanner.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.scanner.history.HistoryEntry
import io.github.martindavinci.lunaris.scanner.history.HistoryRepository
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Takes the history as a flow and a function, so tests do not need a real [HistoryRepository]. */
@HiltViewModel
class SettingsViewModel internal constructor(
    private val settingsStore: SettingsStore,
    private val historyEntries: Flow<List<HistoryEntry>>,
    private val clearHistory: suspend () -> Unit,
) : ViewModel() {

    @Inject
    constructor(settingsStore: SettingsStore, history: HistoryRepository) :
        this(settingsStore, history.entries, { history.clear() })

    private val offerClearHistory = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> =
        combine(settingsStore.settings, offerClearHistory) { settings, offer -> SettingsUiState(settings, offer) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetTheme -> viewModelScope.launch { settingsStore.setTheme(action.theme) }
            is SettingsAction.SetKeepHistory -> setKeepHistory(action.keep)
            is SettingsAction.SetVibrateOnScan -> viewModelScope.launch { settingsStore.setVibrateOnScan(action.vibrate) }
            SettingsAction.ClearHistory -> {
                offerClearHistory.value = false
                viewModelScope.launch { clearHistory() }
            }
            SettingsAction.DismissClearHistory -> offerClearHistory.value = false
        }
    }

    /** Turning history off never deletes anything: the entries stay unless the user clears them. */
    private fun setKeepHistory(keep: Boolean) {
        viewModelScope.launch {
            settingsStore.setKeepHistory(keep)
            offerClearHistory.value = !keep && historyEntries.first().isNotEmpty()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
