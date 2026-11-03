package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.pdf.actions.DocumentActions
import io.github.martindavinci.lunaris.pdf.actions.DocumentCopier
import io.github.martindavinci.lunaris.pdf.recents.DocumentThumbnails
import io.github.martindavinci.lunaris.pdf.recents.ReadingSpot
import io.github.martindavinci.lunaris.pdf.recents.RecentVisit
import io.github.martindavinci.lunaris.pdf.recents.RecentsStore
import io.github.martindavinci.lunaris.pdf.recents.ThumbnailStore
import io.github.martindavinci.lunaris.pdf.session.CurrentDocument
import io.github.martindavinci.lunaris.pdf.settings.SettingsStore
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState
import io.github.martindavinci.lunaris.pdf.tts.SpeechEngine
import io.github.martindavinci.lunaris.pdf.tts.SpokenSentence
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

/** Takes the stores as flows and functions, so tests do not need real ones. */
@HiltViewModel
class ReaderViewModel internal constructor(
    val viewer: ViewerChannel,
    currentDocument: CurrentDocument,
    savedState: SavedStateHandle,
    nightPages: Flow<Boolean>,
    private val setNightPages: suspend (Boolean) -> Unit,
    topButtonEnabled: Flow<Boolean>,
    touchRecent: suspend (RecentVisit) -> Unit,
    savePosition: suspend (ReadingSpot) -> Unit,
    thumbnails: DocumentThumbnails,
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
        thumbnails: ThumbnailStore,
        speechEngine: Provider<SpeechEngine>,
        documents: DocumentCopier,
    ) : this(
        engine,
        currentDocument,
        savedState,
        settingsStore.settings.map { it.nightPages }.distinctUntilChanged(),
        settingsStore::setNightPages,
        settingsStore.settings.map { it.topButton }.distinctUntilChanged(),
        recents::touch,
        recents::savePosition,
        thumbnails,
        speechEngine::get,
        documents,
    )

    private val position = ReaderPosition(savedState, viewModelScope, savePosition, SAVE_POSITION_DELAY_MILLIS)
    private val mutableState = MutableStateFlow(position.initialState(currentDocument.document.value))
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    /** Kept in memory only, to reopen the document if the page reloads. Never stored. */
    private var password: String? = null
    private var pageReady = false
    private var barSpace: ViewerCommand.SetBarSpace? = null
    private val files = ReaderFiles(viewModelScope, documents) { change -> mutableState.update(change) }
    private val speech = ReaderSpeech(viewModelScope, viewer, speechEngine, ::onReadAloud, ::onSentence)
    private val search = ReaderSearch(viewer, { state.value }) { change -> mutableState.update(change) }
    private val jumps = ReaderJumps(viewer, { state.value }) { change -> mutableState.update(change) }
    private val recents = ReaderRecents(viewModelScope, touchRecent, thumbnails) {
        viewer.send(ViewerCommand.MakeThumbnail)
    }

    init {
        viewModelScope.launch { viewer.events.collect(::onEvent) }
        viewModelScope.launch { nightPages.collect(::onNight) }
        viewModelScope.launch {
            topButtonEnabled.collect { on -> mutableState.update { it.copy(topButtonEnabled = on) } }
        }
        viewModelScope.launch {
            delay(READY_TIMEOUT_MILLIS)
            if (!pageReady) onEvent(ViewerEvent.LoadFailed)
        }
    }

    fun onAction(action: ReaderAction) {
        when (action) {
            ReaderAction.BackToTop -> jumps.goTo(1)
            is ReaderAction.GoToPage -> jumps.goTo(clampPage(action.page))
            ReaderAction.ReturnFromJump -> jumps.goBack()
            ReaderAction.OpenSearch -> search.open()
            ReaderAction.CloseSearch -> search.close()
            is ReaderAction.SubmitSearch -> search.submit(action.query)
            ReaderAction.FindNext -> search.find(previous = false)
            ReaderAction.FindPrevious -> search.find(previous = true)
            ReaderAction.ToggleMatchCase -> search.toggleMatchCase()
            ReaderAction.ToggleWholeWords -> search.toggleWholeWords()
            is ReaderAction.SetBarSpace -> setBarSpace(ViewerCommand.SetBarSpace(action.top, action.bottom))
            is ReaderAction.SubmitPassword -> submitPassword(action.password)
            ReaderAction.DismissLink -> mutableState.update { it.copy(pendingLink = null) }
            ReaderAction.ToggleNight -> viewModelScope.launch { setNightPages(!state.value.night) }
            ReaderAction.StartReadAloud -> speech.start(state.value.page, state.value.pages)
            ReaderAction.PauseReadAloud -> speech.pause()
            ReaderAction.ResumeReadAloud -> speech.resume()
            ReaderAction.NextReadAloudPage -> speech.next()
            ReaderAction.PreviousReadAloudPage -> speech.previous()
            ReaderAction.StopReadAloud -> speech.stop()
            ReaderAction.NextReadAloudRate -> mutableState.update { it.copy(readAloudRate = speech.nextRate()) }
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
            is ViewerEvent.FindResult -> search.onResult(event)
            is ViewerEvent.LinkTapped -> mutableState.update { it.copy(pendingLink = PendingLink.of(event.url)) }
            is ViewerEvent.PageText -> speech.onPageText(event)
            is ViewerEvent.ScrollChanged -> mutableState.update { it.copy(scrolling = event.moving, scrollingDown = event.down) }
            is ViewerEvent.Jumped -> jumps.moved(event.from, event.to)
            ViewerEvent.ChapterCrossed -> mutableState.update { it.copy(chapterTicks = it.chapterTicks + 1) }
            is ViewerEvent.Thumbnail -> if (password == null) recents.onThumbnail(state.value.uri, event.jpeg)
            ViewerEvent.Tapped -> mutableState.update {
                if (it.search != null) it else it.copy(chromeVisible = !it.chromeVisible)
            }
        }
    }

    private fun onPageReady() {
        pageReady = true
        viewer.send(ViewerCommand.Open(password, state.value.page, position.scale))
        viewer.send(ViewerCommand.SetNight(state.value.night))
        barSpace?.let(viewer::send)
    }

    private fun onLoaded(event: ViewerEvent.Loaded) {
        val outlineArrived = event.outline.isNotEmpty() && event.outline != state.value.outline
        mutableState.update {
            it.copy(pages = event.pages, loading = false, outline = event.outline.ifEmpty { it.outline })
        }
        state.value.uri?.let { recents.onLoaded(RecentVisit(it, state.value.name, event.pages, password != null)) }
        if (outlineArrived) savePosition(position.scale)
    }

    private fun onPageChanged(event: ViewerEvent.PageChanged) {
        mutableState.update { it.copy(page = event.page, jump = jumps.whileReading(it.jump, event.page)) }
        savePosition(event.scale)
    }

    private fun savePosition(scale: String) {
        val chapter = state.value.chapter?.takeIf { password == null }.orEmpty()
        position.onPageChanged(state.value.uri, state.value.page, scale, chapter)
    }

    private fun setBarSpace(space: ViewerCommand.SetBarSpace) {
        barSpace = space
        if (pageReady) viewer.send(space)
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

    /** The document follows the voice. */
    private fun onReadAloud(value: ReadAloudState) {
        mutableState.update { it.copy(readAloud = value) }
        if (value is ReadAloudState.Reading && value.page != state.value.page) {
            viewer.send(ViewerCommand.GoToPage(value.page))
        }
    }

    private fun onSentence(sentence: SpokenSentence?) {
        mutableState.update { it.copy(spoken = sentence) }
        viewer.send(sentence?.let { ViewerCommand.MarkSentence(it.page, it.text) } ?: ViewerCommand.ClearMark)
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
