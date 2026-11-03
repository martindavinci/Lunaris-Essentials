package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import io.github.martindavinci.lunaris.pdf.actions.DocumentCopier
import io.github.martindavinci.lunaris.pdf.recents.RecentsStore
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import io.github.martindavinci.lunaris.pdf.settings.SettingsStore
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState
import io.github.martindavinci.lunaris.pdf.tts.SpeechEngine
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEngine
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEvent
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Takes the night setting as a flow and a function, so tests do not need a real store. */
@HiltViewModel
class ReaderViewModel internal constructor(
    val viewer: ViewerChannel,
    currentDocument: CurrentDocument,
    savedState: SavedStateHandle,
    nightPages: Flow<Boolean>,
    private val setNightPages: suspend (Boolean) -> Unit,
    private val touchRecent: suspend (String, String) -> Unit,
    savePosition: suspend (String, Int, String) -> Unit,
    speechEngine: () -> SpeechEngine,
    documents: DocumentActions,
) : ViewModel() {

    @Inject
    constructor(
        engine: ViewerEngine,
        currentDocument: CurrentDocument,
        savedState: SavedStateHandle,
        settingsStore: SettingsStore,
        recents: RecentsStore,
        speechEngine: Provider<SpeechEngine>,
        documents: DocumentCopier,
    ) : this(
        engine,
        currentDocument,
        savedState,
        settingsStore.settings.map { it.nightPages }.distinctUntilChanged(),
        settingsStore::setNightPages,
        recents::touch,
        recents::savePosition,
        speechEngine::get,
        documents,
    )

    private val position = ReaderPosition(savedState, viewModelScope, savePosition, SAVE_POSITION_DELAY_MILLIS)
    private val mutableState = MutableStateFlow(position.initialState(currentDocument.document.value))
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    /** Kept in memory only, to reopen the document if the page reloads. Never stored. */
    private var password: String? = null
    private var pageReady = false
    private var joinedRecents = false
    private val files = ReaderFiles(viewModelScope, documents) { change -> mutableState.update(change) }
    private val speech = ReaderSpeech(viewModelScope, viewer, speechEngine, ::onReadAloud)

    init {
        viewModelScope.launch { viewer.events.collect(::onEvent) }
        viewModelScope.launch { nightPages.collect(::onNight) }
        viewModelScope.launch {
            delay(READY_TIMEOUT_MILLIS)
            if (!pageReady) onEvent(ViewerEvent.LoadFailed)
        }
    }

    fun onAction(action: ReaderAction) {
        when (action) {
            ReaderAction.BackToTop -> viewer.send(ViewerCommand.GoToPage(1))
            is ReaderAction.GoToPage -> viewer.send(ViewerCommand.GoToPage(clampPage(action.page)))
            ReaderAction.OpenSearch -> mutableState.update { it.copy(search = SearchState(), chromeVisible = true) }
            ReaderAction.CloseSearch -> {
                mutableState.update { it.copy(search = null) }
                viewer.send(ViewerCommand.CloseFind)
            }
            is ReaderAction.SubmitSearch -> submitSearch(action.query)
            ReaderAction.FindNext -> find(previous = false)
            ReaderAction.FindPrevious -> find(previous = true)
            is ReaderAction.SubmitPassword -> submitPassword(action.password)
            ReaderAction.DismissLink -> mutableState.update { it.copy(pendingLink = null) }
            ReaderAction.ToggleNight -> viewModelScope.launch { setNightPages(!state.value.night) }
            ReaderAction.StartReadAloud -> speech.start(state.value.page, state.value.pages)
            ReaderAction.PauseReadAloud -> speech.pause()
            ReaderAction.ResumeReadAloud -> speech.resume()
            ReaderAction.NextReadAloudPage -> speech.next()
            ReaderAction.PreviousReadAloudPage -> speech.previous()
            ReaderAction.StopReadAloud -> speech.stop()
            is ReaderAction.SaveCopyTo -> state.value.uri?.let { files.saveCopy(it, action.uri) }
            ReaderAction.Share -> state.value.uri?.let { files.share(it, state.value.name) }
            ReaderAction.ShareStarted -> mutableState.update { it.copy(shareUri = null) }
            ReaderAction.NoticeShown -> mutableState.update { it.copy(notice = null) }
        }
    }

    private fun onEvent(event: ViewerEvent) {
        when (event) {
            ViewerEvent.Ready -> onPageReady()
            is ViewerEvent.Loaded -> onLoaded(event)
            is ViewerEvent.PageChanged -> onPageChanged(event)
            is ViewerEvent.PasswordNeeded -> mutableState.update {
                it.copy(loading = false, passwordPrompt = PasswordPrompt(event.wrong))
            }
            ViewerEvent.LoadFailed -> {
                speech.stop()
                mutableState.update { it.copy(loading = false, error = ReaderError.CANNOT_OPEN) }
            }
            ViewerEvent.RendererKilled -> {
                speech.stop()
                mutableState.update { it.copy(loading = true, viewRevision = it.viewRevision + 1) }
            }
            is ViewerEvent.FindResult -> mutableState.update { state ->
                state.copy(
                    search = state.search?.copy(current = event.current, total = event.total, searching = !event.done),
                )
            }
            is ViewerEvent.LinkTapped -> mutableState.update { it.copy(pendingLink = PendingLink.of(event.url)) }
            is ViewerEvent.PageText -> speech.onPageText(event)
            ViewerEvent.Tapped -> mutableState.update {
                if (it.search != null) it else it.copy(chromeVisible = !it.chromeVisible)
            }
        }
    }

    private fun onPageReady() {
        pageReady = true
        viewer.send(ViewerCommand.Open(password, state.value.page, position.scale))
        viewer.send(ViewerCommand.SetNight(state.value.night))
    }

    /** Only a file that really opened joins the recents. */
    private fun onLoaded(event: ViewerEvent.Loaded) {
        mutableState.update {
            it.copy(pages = event.pages, loading = false, outline = event.outline.ifEmpty { it.outline })
        }
        val uri = state.value.uri
        if (!joinedRecents && uri != null) {
            joinedRecents = true
            viewModelScope.launch { touchRecent(uri, state.value.name) }
        }
    }

    private fun onPageChanged(event: ViewerEvent.PageChanged) {
        mutableState.update { it.copy(page = event.page) }
        position.onPageChanged(state.value.uri, event.page, event.scale)
    }

    private fun onNight(on: Boolean) {
        mutableState.update { it.copy(night = on) }
        if (pageReady) viewer.send(ViewerCommand.SetNight(on))
    }

    private fun submitPassword(value: String) {
        password = value
        mutableState.update { it.copy(passwordPrompt = null, loading = true, canPrint = false) }
        viewer.send(ViewerCommand.Open(value, state.value.page, position.scale))
    }

    private fun submitSearch(query: String) {
        mutableState.update { it.copy(search = (it.search ?: SearchState()).copy(query = query)) }
        find(previous = false)
    }

    private fun find(previous: Boolean) {
        val query = state.value.search?.query?.takeIf { it.isNotBlank() } ?: return
        mutableState.update { it.copy(search = it.search?.copy(searching = true)) }
        viewer.send(ViewerCommand.Find(query, previous))
    }

    /** The document follows the voice. */
    private fun onReadAloud(value: ReadAloudState) {
        mutableState.update { it.copy(readAloud = value) }
        if (value is ReadAloudState.Reading && value.page != state.value.page) {
            viewer.send(ViewerCommand.GoToPage(value.page))
        }
    }

    override fun onCleared() {
        speech.shutdown()
    }

    private fun clampPage(page: Int): Int = page.coerceIn(1, state.value.pages.coerceAtLeast(1))

    companion object {
        /** The viewer page comes from the APK: this long means it will never load. A slow file may take longer. */
        internal const val READY_TIMEOUT_MILLIS = 15_000L
        internal const val SAVE_POSITION_DELAY_MILLIS = 500L
    }
}
