package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.archives.browse.EntrySearch
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.browse.FolderView
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.di.Background
import io.github.martindavinci.lunaris.archives.engine.ArchiveProblem
import io.github.martindavinci.lunaris.archives.feature.Routes
import io.github.martindavinci.lunaris.archives.files.EntryOpener
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.session.OpenOutcome
import io.github.martindavinci.lunaris.archives.session.PendingOpen
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One archive, browsed folder by folder (frames 03–07). */
@OptIn(FlowPreview::class)
@HiltViewModel
class BrowserViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: ArchiveSession,
    sources: SourcesPort,
    private val preferences: BrowserPreferences,
    private val recents: RecentsPort,
    opener: EntryOpener,
    @param:Background private val background: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private val loader = BrowserLoader(session, sources, _state)
    private val opening = BrowserOpening(viewModelScope, session, opener, _state, ::relist)
    private val archiveId: String? = savedState.get<String>(Routes.ARG_ARCHIVE_ID)
    private var archive: OpenArchive? = null
    private var view: FolderView? = null
    private var pending: PendingOpen? = null
    private val queries = MutableStateFlow<String?>(null)
    private val visitedPath = MutableStateFlow<List<String>?>(null)

    init {
        viewModelScope.launch {
            _state.update { it.copy(sort = preferences.settings.first().sort) }
            apply(loader.load(archiveId))
        }
        viewModelScope.launch {
            queries.debounce(SEARCH_DEBOUNCE_MILLIS).collect { query ->
                val current = view
                val hits = if (query == null || current == null) emptyList() else withContext(background) { EntrySearch.search(current, query) }
                _state.update { it.copy(hits = if (it.query == null) emptyList() else hits) }
            }
        }
        viewModelScope.launch {
            visitedPath.filterNotNull().debounce(VISIT_DEBOUNCE_MILLIS).collect { path -> archive?.let { recents.visited(it, path) } }
        }
    }

    fun onAction(action: BrowserAction) {
        when (action) {
            is BrowserAction.OpenRow -> if (action.row.isFolder) navigate(action.row.path) else openEntry(action.row, chooser = false)
            is BrowserAction.OpenWith -> openEntry(action.row, chooser = true)
            BrowserAction.CancelOpening -> opening.cancel()
            BrowserAction.Up -> up()
            is BrowserAction.GoToCrumb -> navigate(_state.value.path.take(action.index))
            is BrowserAction.LongPress -> _state.update { BrowserContent.toggle(BrowserContent.clearSelection(it).copy(selection = emptySet()), action.row) }
            is BrowserAction.Toggle -> _state.update { BrowserContent.toggle(it, action.row) }
            BrowserAction.SelectAll -> _state.update(BrowserContent::selectAll)
            BrowserAction.ClearSelection -> _state.update(BrowserContent::clearSelection)
            BrowserAction.OpenSearch -> setQuery("")
            is BrowserAction.SetQuery -> setQuery(action.text)
            BrowserAction.CloseSearch -> { _state.update { it.copy(query = null, hits = emptyList()) }; queries.value = null }
            BrowserAction.ShowInfo -> _state.update { it.copy(sheet = BrowserSheet.Info) }
            BrowserAction.ShowSort -> _state.update { it.copy(sheet = BrowserSheet.Sort) }
            is BrowserAction.ShowRowSheet -> _state.update { it.copy(sheet = BrowserSheet.Row(action.row)) }
            BrowserAction.DismissSheet -> _state.update { it.copy(sheet = null) }
            is BrowserAction.SetSort -> setSort(action.order)
            is BrowserAction.LaunchHandled -> opening.launchHandled(action.ok)
            BrowserAction.NoticeShown -> _state.update { it.copy(notice = null) }
            else -> Unit
        }
    }

    private fun apply(outcome: OpenOutcome?) {
        when (outcome) {
            null -> Unit
            is OpenOutcome.Opened -> show(outcome.archive)
            is OpenOutcome.NeedsPassword -> askPassword(outcome.pending, wrong = false)
            is OpenOutcome.WrongPassword -> askPassword(outcome.pending, wrong = true)
            is OpenOutcome.NeedsCopy -> viewModelScope.launch { apply(loader.copyAndOpen(outcome.uri, outcome.bytes)) }
            is OpenOutcome.Problem -> _state.update { it.copy(problem = outcome.problem, header = null, password = null) }
        }
    }

    private fun show(opened: OpenArchive) {
        archive = opened
        val folders = FolderView(opened.index).also { view = it }
        _state.update { state ->
            val named = state.copy(
                title = opened.source.name, badge = opened.index.formatLabel, header = BrowserContent.header(opened, folders),
                loading = LoadState.Ready, hiddenUnsafe = folders.hiddenUnsafe, capped = opened.index.cappedAtLimit,
                partial = opened.index.partial, problem = null, password = null,
            )
            BrowserContent.showFolder(named, folders, emptyList())
        }
        visitedPath.value = emptyList()
    }

    private fun askPassword(open: PendingOpen, wrong: Boolean) {
        pending = open
        _state.update {
            it.copy(title = open.source.name, problem = null, password = PasswordPrompt(open.source.name, PasswordTarget.ARCHIVE, wrong))
        }
    }

    private fun openEntry(row: FolderRow, chooser: Boolean) {
        val open = archive ?: return
        _state.update { it.copy(sheet = null) }
        opening.open(open, row, chooser)
    }

    /** The archive is not the one that was listed any more: list it again and say so. */
    private fun relist() {
        archive?.let { session.forgetIndex(it.source); session.close(it.id) }
        archive = null
        _state.update { it.copy(notice = BrowserNotice.Changed, loading = LoadState.Reading) }
        viewModelScope.launch { apply(loader.load(archiveId)) }
    }

    private fun navigate(path: List<String>) {
        val folders = view ?: return
        _state.update { BrowserContent.showFolder(it, folders, path) }
        visitedPath.value = _state.value.path
    }

    private fun up() {
        val path = _state.value.path
        if (path.isEmpty()) _state.update { it.copy(exit = true) } else navigate(path.dropLast(1))
    }

    private fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        queries.value = text
    }

    private fun setSort(order: SortOrder) {
        _state.update { state -> view?.let { BrowserContent.showFolder(state.copy(sort = order, sheet = null), it, state.path) } ?: state.copy(sort = order) }
        viewModelScope.launch { preferences.saveSort(order) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 150L
        const val VISIT_DEBOUNCE_MILLIS = 1_000L
    }
}

/** [ArchiveProblem.GONE] for a browser route that points at nothing. */
internal val GONE = OpenOutcome.Problem(ArchiveProblem.GONE)
