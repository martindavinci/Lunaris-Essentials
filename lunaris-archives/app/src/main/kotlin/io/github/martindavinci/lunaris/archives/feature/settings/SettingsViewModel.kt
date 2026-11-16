package io.github.martindavinci.lunaris.archives.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.archives.di.Background
import io.github.martindavinci.lunaris.archives.files.CacheJanitor
import io.github.martindavinci.lunaris.archives.settings.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Frame 18: theme, recents on or off, and emptying the temporary files (copies, shares, saved listings). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val janitor: CacheJanitor,
    @param:Background private val background: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { store.settings.collect { s -> _state.update { it.copy(settings = s) } } }
        refreshSize()
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetTheme -> viewModelScope.launch { store.setTheme(action.mode) }
            is SettingsAction.SetRememberRecents -> viewModelScope.launch { store.setRememberRecents(action.on) }
            SettingsAction.ClearTemporaryFiles -> viewModelScope.launch {
                withContext(background) { janitor.clearAll() }
                _state.update { it.copy(notice = SettingsNotice.Cleared) }
                refreshSize()
            }
            SettingsAction.NoticeShown -> _state.update { it.copy(notice = null) }
        }
    }

    private fun refreshSize() {
        viewModelScope.launch {
            val bytes = withContext(background) { janitor.sizeBytes() }
            _state.update { it.copy(tempBytes = bytes) }
        }
    }
}
