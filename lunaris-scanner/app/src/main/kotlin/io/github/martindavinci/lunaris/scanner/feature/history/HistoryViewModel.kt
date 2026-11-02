package io.github.martindavinci.lunaris.scanner.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.scanner.history.HistoryRepository
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.parse.ContentParser
import io.github.martindavinci.lunaris.scanner.session.CurrentResult
import io.github.martindavinci.lunaris.scanner.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    settingsStore: SettingsStore,
    private val currentResult: CurrentResult,
) : ViewModel() {

    // The search field reads its text back from this state, so typing has to update it synchronously.
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = mutableState.asStateFlow()

    init {
        val items = history.entries
            .map { entries ->
                entries.map { entry -> entry.toItem(ContentParser.parse(ScannedCode(entry.text, entry.format))) }
            }
            .flowOn(Dispatchers.Default)
        viewModelScope.launch {
            combine(items, settingsStore.settings) { allItems, settings -> allItems to settings.keepHistory }
                .collect { (allItems, keepHistory) ->
                    mutableState.update { it.copy(loaded = true, allItems = allItems, keepHistory = keepHistory) }
                }
        }
    }

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.QueryChanged -> mutableState.update { it.copy(query = action.query) }
            is HistoryAction.Open -> currentResult.set(action.item.code)
            is HistoryAction.Delete -> viewModelScope.launch { history.delete(action.id) }
            HistoryAction.RequestClear -> mutableState.update { it.copy(confirmingClear = true) }
            HistoryAction.DismissClear -> mutableState.update { it.copy(confirmingClear = false) }
            HistoryAction.ConfirmClear -> {
                mutableState.update { it.copy(confirmingClear = false) }
                viewModelScope.launch { history.clear() }
            }
        }
    }
}
