package io.github.martindavinci.lunaris.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.pdf.open.DocumentOpener
import io.github.martindavinci.lunaris.pdf.settings.AppSettings
import io.github.martindavinci.lunaris.pdf.settings.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [settings] is null until the stored settings have been read. [opening]: a file from another app is being prepared. */
data class MainUiState(val settings: AppSettings? = null, val opening: Boolean = false)

@HiltViewModel
class MainViewModel internal constructor(
    settings: Flow<AppSettings>,
    private val openNew: suspend (String) -> Unit,
) : ViewModel() {

    @Inject
    constructor(settingsStore: SettingsStore, opener: DocumentOpener) : this(settingsStore.settings, opener::openNew)

    private val opening = MutableStateFlow(false)
    private val openReader = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<MainUiState> = combine(settings, opening) { current, busy -> MainUiState(current, busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MainUiState())

    /** Fires only while the screens are up. A file that started the app makes the Reader the first screen instead. */
    val readerRequests: SharedFlow<Unit> = openReader.asSharedFlow()

    fun openFromIntent(uri: String) {
        opening.value = true
        viewModelScope.launch {
            openNew(uri)
            opening.value = false
            openReader.tryEmit(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
