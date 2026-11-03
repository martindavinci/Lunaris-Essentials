package io.github.martindavinci.lunaris.pdf.feature.recents

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import io.github.martindavinci.lunaris.pdf.actions.DocumentCopier
import io.github.martindavinci.lunaris.pdf.open.DocumentOpener
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import io.github.martindavinci.lunaris.pdf.recents.RecentsHome
import io.github.martindavinci.lunaris.pdf.recents.RecentsStore
import io.github.martindavinci.lunaris.pdf.recents.ThumbnailStore
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Takes the store and the opener as functions, so tests need neither a file nor a provider. */
@HiltViewModel
class RecentsViewModel internal constructor(
    entries: Flow<List<RecentEntry>>,
    private val remove: suspend (String) -> Unit,
    private val clear: suspend () -> Unit,
    private val openNew: suspend (String) -> Unit,
    private val openRecent: suspend (RecentEntry) -> Boolean,
    private val setPinned: suspend (String, Boolean) -> Boolean,
    private val restore: suspend (RecentEntry) -> Unit,
    private val documents: DocumentActions,
    private val thumbnail: suspend (String) -> ImageBitmap?,
) : ViewModel() {

    @Inject
    constructor(
        store: RecentsStore,
        opener: DocumentOpener,
        documents: DocumentCopier,
        thumbnails: ThumbnailStore,
    ) : this(
        store.entries,
        store::remove,
        store::clear,
        opener::openNew,
        opener::openRecent,
        store::setPinned,
        store::restore,
        documents,
        { uri -> loadThumbnail(thumbnails.fileOf(uri)) },
    )

    private val flags = MutableStateFlow(RecentsUiState())

    val state: StateFlow<RecentsUiState> = combine(entries, flags) { list, current ->
        current.copy(entries = list, home = RecentsHome.of(list, current.query))
    }
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
            RecentsAction.OpenSearch -> flags.update { it.copy(query = "") }
            RecentsAction.CloseSearch -> flags.update { it.copy(query = null) }
            is RecentsAction.SetQuery -> flags.update { it.copy(query = action.query) }
            is RecentsAction.SetPinned -> viewModelScope.launch {
                if (!setPinned(action.entry.uri, action.on)) flags.update { it.copy(notice = RecentsNotice.PinLimit) }
            }
            is RecentsAction.RemoveEntry -> viewModelScope.launch {
                remove(action.entry.uri)
                flags.update { it.copy(notice = RecentsNotice.Removed(action.entry)) }
            }
            is RecentsAction.UndoRemove -> viewModelScope.launch { restore(action.entry) }
            is RecentsAction.ShareEntry -> viewModelScope.launch {
                val shared = documents.prepareShare(action.entry.uri, action.entry.name)
                flags.update { if (shared != null) it.copy(shareUri = shared) else it.copy(notice = RecentsNotice.ShareFailed) }
            }
            RecentsAction.ShareStarted -> flags.update { it.copy(shareUri = null) }
            RecentsAction.NoticeShown -> flags.update { it.copy(notice = null) }
        }
    }

    /** The picture of a document's first page, if there is one yet. */
    suspend fun thumbnailOf(uri: String): ImageBitmap? = thumbnail(uri)

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

        /** A damaged picture is the same as none. */
        suspend fun loadThumbnail(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.path)?.asImageBitmap()
        }
    }
}
