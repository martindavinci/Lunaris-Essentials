package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.pdf.open.DocumentOpener
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import io.github.martindavinci.lunaris.pdf.recents.RecentsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Takes the store and the opener as functions, so tests need neither a file nor a provider. */
@HiltViewModel
class RecentsViewModel internal constructor(
    entries: Flow<List<RecentEntry>>,
    private val remove: suspend (String) -> Unit,
    private val clear: suspend () -> Unit,
    private val openNew: suspend (String) -> Unit,
    private val openRecent: suspend (RecentEntry) -> Boolean,
) : ViewModel() {

    @Inject
    constructor(store: RecentsStore, opener: DocumentOpener) : this(
        store.entries,
        store::remove,
        store::clear,
        opener::openNew,
        opener::openRecent,
    )

    private val flags = MutableStateFlow(RecentsUiState())

    val state: StateFlow<RecentsUiState> = combine(entries, flags) { list, current -> current.copy(entries = list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RecentsUiState())

    fun onAction(action: RecentsAction) {
        when (action) {
            is RecentsAction.OpenPicked -> viewModelScope.launch {
                openNew(action.uri)
                flags.update { it.copy(openReader = true) }
            }
            is RecentsAction.OpenEntry -> openEntry(action.entry)
            RecentsAction.ReaderOpened -> flags.update { it.copy(openReader = false) }
            RecentsAction.MessageShown -> flags.update { it.copy(entryGone = false) }
            RecentsAction.ClearAll -> viewModelScope.launch { clear() }
        }
    }

    private fun openEntry(entry: RecentEntry) {
        viewModelScope.launch {
            if (openRecent(entry)) {
                flags.update { it.copy(openReader = true) }
            } else {
                remove(entry.uri)
                flags.update { it.copy(entryGone = true) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
