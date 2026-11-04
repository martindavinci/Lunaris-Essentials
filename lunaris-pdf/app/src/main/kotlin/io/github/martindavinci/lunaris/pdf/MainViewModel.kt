package io.github.martindavinci.lunaris.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.pdf.open.DocumentOpener
import io.github.martindavinci.lunaris.pdf.session.CreateInbox
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
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
    private val hasUnsavedEdits: () -> Boolean,
    private val putInCreateList: (List<String>) -> Unit = {},
) : ViewModel() {

    @Inject
    constructor(settingsStore: SettingsStore, opener: DocumentOpener, current: CurrentDocument, inbox: CreateInbox) :
        this(settingsStore.settings, opener::openNew, { current.hasUnsavedEdits }, inbox::put)

    private val opening = MutableStateFlow(false)
    private val openReader = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refused = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val openCreate = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<MainUiState> = combine(settings, opening) { current, busy -> MainUiState(current, busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MainUiState())

    /** Fires only while the screens are up. A file that started the app makes the Reader the first screen instead. */
    val readerRequests: SharedFlow<Unit> = openReader.asSharedFlow()

    /** Files were shared to the app while its screens are up. */
    val createRequests: SharedFlow<Unit> = openCreate.asSharedFlow()

    /** A file came while the Reader holds unsaved changes, which it would have replaced. */
    val refusedDocuments: SharedFlow<Unit> = refused.asSharedFlow()

    fun openFromIntent(uri: String) {
        if (hasUnsavedEdits()) {
            refused.tryEmit(Unit)
            return
        }
        opening.value = true
        viewModelScope.launch {
            openNew(uri)
            opening.value = false
            openReader.tryEmit(Unit)
        }
    }

    /** Waits for the Create PDF screen. [refusedDocuments] tells if something would be lost. */
    fun shareToCreate(uris: List<String>) {
        if (hasUnsavedEdits()) {
            refused.tryEmit(Unit)
            return
        }
        putInCreateList(uris)
        openCreate.tryEmit(Unit)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
