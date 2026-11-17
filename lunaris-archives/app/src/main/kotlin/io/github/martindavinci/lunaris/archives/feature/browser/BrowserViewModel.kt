package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.archives.browse.EntrySearch
import io.github.martindavinci.lunaris.archives.browse.FolderRow
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
import kotlinx.coroutines.CoroutineStart
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

    private val loader = BrowserLoader(session, sources, _state, background)
    private val opening = BrowserOpening(viewModelScope, session, opener, _state, ::relist)
    private val sharing = BrowserSharing(viewModelScope, session, opener, _state)
    private val folders = BrowserFolders(viewModelScope, background, _state) { path -> visitedPath.value = path }
    private val archiveId: String? = savedState.get<String>(Routes.ARG_ARCHIVE_ID)
    private var archive: OpenArchive? = null
    private var pending: PendingOpen? = null
    private var waitingExtract: LongArray? = null
    private val queries = MutableStateFlow<String?>(null)
    private val visitedPath = MutableStateFlow<List<String>?>(null)

    init {
        viewModelScope.launch {
            _state.update { it.copy(sort = preferences.settings.first().sort) }
            apply(loader.load(archiveId))
        }
        viewModelScope.launch {
            queries.debounce(SEARCH_DEBOUNCE_MILLIS).collect { query ->
                val current = folders.view
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
            is BrowserAction.Share -> archive?.let { a -> sharing.share(a, action.rows.mapNotNull { it.entry }); _state.update { it.copy(sheet = null) } }
            BrowserAction.ShareSelection -> archive?.let { sharing.shareSelection(it) }
            BrowserAction.ShareArchive -> archive?.let(sharing::shareArchive)
            BrowserAction.ExtractAll -> archive?.let { a -> extract(a.index.entries.map { it.index }.toLongArray()) }
            is BrowserAction.ExtractRows -> extractRows(action.rows.filter { !it.isFolder }.mapNotNull { it.entry?.index }, action.rows.filter { it.isFolder }.map { it.path })
            BrowserAction.ExtractSelection -> _state.value.let { extractRows(it.selection.orEmpty(), it.selectedFolders) }
            is BrowserAction.SubmitPassword -> submitPassword(action.password, action.remember)
            BrowserAction.DismissPassword -> dismissPassword()
            BrowserAction.PasswordEdited -> _state.update { s -> s.copy(password = s.password?.copy(wrong = false)) }
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
            BrowserAction.Reload -> relist(quiet = true)
            else -> Unit
        }
    }

    private suspend fun apply(outcome: OpenOutcome?) {
        when (outcome) {
            null -> Unit
            is OpenOutcome.Opened -> show(outcome.archive)
            is OpenOutcome.NeedsPassword -> askPassword(outcome.pending, wrong = false)
            is OpenOutcome.WrongPassword -> askPassword(outcome.pending, wrong = true)
            is OpenOutcome.NeedsCopy -> viewModelScope.launch { apply(loader.copyAndOpen(outcome.uri, outcome.bytes)) }
            is OpenOutcome.Problem -> _state.update { it.copy(problem = outcome.problem, header = null, password = null) }
        }
    }

    private suspend fun show(opened: OpenArchive) {
        archive = opened
        val tree = folders.load(opened.index)
        val start = loader.takeStart()
        val header = withContext(background) { BrowserContent.header(opened, tree) }   // passes over 200 000 entries
        _state.update { state ->
            state.copy(
                title = opened.source.name, badge = opened.index.formatLabel, header = header, archiveUri = opened.source.uri,
                loading = LoadState.Ready, hiddenUnsafe = tree.hiddenUnsafe, capped = opened.index.cappedAtLimit,
                partial = opened.index.partial, problem = null, password = null,
            )
        }
        folders.show(start?.startPath.orEmpty())
        if (start?.extractAll == true) onAction(BrowserAction.ExtractAll)
    }

    private fun askPassword(open: PendingOpen, wrong: Boolean) {
        pending = open
        _state.update {
            it.copy(
                title = open.source.name, badge = open.source.kindHint.label, problem = null,
                password = PasswordPrompt(open.source.name, PasswordTarget.ARCHIVE, wrong),
            )
        }
    }

    private fun openEntry(row: FolderRow, chooser: Boolean) {
        val open = archive ?: return
        _state.update { it.copy(sheet = null) }
        opening.open(open, row, chooser)
    }

    /** The archive is not the one that was listed any more: list it again and say so. */
    private fun relist(quiet: Boolean = false) {
        archive?.let { session.forgetIndex(it.source); session.close(it.id) }
        archive = null
        _state.update { it.copy(notice = if (quiet) it.notice else BrowserNotice.Changed, loading = LoadState.Reading) }
        viewModelScope.launch { apply(loader.load(archiveId)) }
    }

    private fun extractRows(files: Collection<Long>, folders: Collection<List<String>>) {
        val open = archive ?: return
        _state.update(BrowserContent::clearSelection)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            extract(withContext(background) { BrowserContent.indicesFor(open, files, folders) })
        }
    }

    /** Protected entries and no password yet: asked first (RAR4 has none to give: its own message comes later). */
    private fun extract(indices: LongArray) {
        val open = archive ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (needsPassword(open, indices)) {
                waitingExtract = indices
                _state.update { it.copy(sheet = null, password = PasswordPrompt(open.source.name, PasswordTarget.EXTRACT)) }
                return@launch
            }
            _state.update { it.copy(sheet = null, launch = LaunchRequest.Extract(ExtractArgs(open.id, indices))) }
        }
    }

    private suspend fun needsPassword(open: OpenArchive, indices: LongArray): Boolean {
        if (open.index.kind == ArchiveKind.RAR4 || session.passwordFor(open.id)?.also { it.fill(Char(0)) } != null) return false
        return withContext(background) {
            val wanted = indices.toHashSet()
            open.index.entries.any { it.encrypted && it.index in wanted }
        }
    }

    /** [typed] is wiped when the attempt is over; it never enters the state. */
    private fun submitPassword(typed: CharArray, remember: Boolean) {
        val prompt = _state.value.password
        val open = archive
        if (prompt?.target == PasswordTarget.ENTRY && open != null) {
            opening.retry(open, typed, remember)
            return
        }
        if (prompt?.target == PasswordTarget.EXTRACT) {
            val indices = waitingExtract
            waitingExtract = null
            if (open != null && indices != null) session.keepPassword(open.id, typed, remember)
            typed.fill(Char(0))
            _state.update { it.copy(password = null) }
            if (open != null && indices != null) _state.update { it.copy(launch = LaunchRequest.Extract(ExtractArgs(open.id, indices))) }
            return
        }
        val waiting = pending
        if (waiting == null) {
            typed.fill(Char(0))
            return
        }
        _state.update { it.copy(password = prompt?.copy(checking = true, wrong = false)) }
        viewModelScope.launch {
            try {
                apply(session.unlock(waiting, typed, remember, archiveId ?: return@launch))
            } finally {
                typed.fill(Char(0))
            }
        }
    }

    private fun dismissPassword() {
        when (_state.value.password?.target) {
            PasswordTarget.ENTRY -> opening.dismissPassword()
            PasswordTarget.EXTRACT -> {
                waitingExtract = null
                _state.update { it.copy(password = null) }
            }
            else -> _state.update { it.copy(password = null, exit = true) }
        }
    }

    private fun navigate(path: List<String>) = folders.show(path)

    private fun up() {
        val path = _state.value.path
        if (path.isEmpty()) _state.update { it.copy(exit = true) } else navigate(path.dropLast(1))
    }

    private fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        queries.value = text
    }

    private fun setSort(order: SortOrder) {
        _state.update { it.copy(sort = order, sheet = null) }
        folders.show(_state.value.path)
        viewModelScope.launch { preferences.saveSort(order) }
    }

    /** Leaving the browser closes its archive: the index, the password kept with it and any incoming copy. */
    override fun onCleared() {
        archive?.let { session.close(it.id) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 150L
        const val VISIT_DEBOUNCE_MILLIS = 1_000L
    }
}

/** [ArchiveProblem.GONE] for a browser route that points at nothing. */
internal val GONE = OpenOutcome.Problem(ArchiveProblem.GONE)
