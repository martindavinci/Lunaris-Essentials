package io.github.martindavinci.lunaris.archives.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.martindavinci.lunaris.archives.extract.PrefsExtractMarker
import io.github.martindavinci.lunaris.archives.open.ReadCheck
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import io.github.martindavinci.lunaris.archives.recents.RecentsRepository
import io.github.martindavinci.lunaris.archives.recents.SettingsSource
import io.github.martindavinci.lunaris.archives.recents.UriPermissions
import io.github.martindavinci.lunaris.archives.session.ArchiveSession
import io.github.martindavinci.lunaris.archives.session.OpenRequest
import io.github.martindavinci.lunaris.archives.settings.AppSettings
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Frames 01, 02: recents, Continue, search, the row menu, clearing. */
@HiltViewModel
class HomeViewModel(
    private val recents: RecentsRepository,
    settings: Flow<AppSettings>,
    private val permissions: UriPermissions,
    private val session: ArchiveSession,
    private val readCheck: ReadCheck,
    marker: PrefsExtractMarker,
) : ViewModel() {
    @Inject constructor(
        recents: RecentsRepository, settings: SettingsSource, permissions: UriPermissions, session: ArchiveSession,
        readCheck: ReadCheck, marker: PrefsExtractMarker,
    ) : this(recents, settings.settings, permissions, session, readCheck, marker)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private val query = MutableStateFlow<String?>(null)
    private var removed: RecentArchive? = null

    init {
        if (marker.takeRecovered()) _state.update { it.copy(notice = HomeNotice.ExtractionRecovered) }
        viewModelScope.launch {
            combine(recents.recents, settings, query) { list, s, q -> Triple(list, s, q) }.collect { (list, s, q) ->
                val needle = q?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val shown = if (needle.isEmpty()) list else list.filter { it.name.lowercase(Locale.ROOT).contains(needle) }
                _state.update {
                    it.copy(entries = shown, continueItem = list.firstOrNull(), query = q, rememberRecents = s.rememberRecents)
                }
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OpenPicked -> navigate(HomeNav.Browser(session.register(OpenRequest(action.uri, null, null, null))))
            is HomeAction.OpenRecent -> openRecent(action.item, extractAll = false)
            HomeAction.OpenSearch -> query.value = ""
            HomeAction.CloseSearch -> query.value = null
            is HomeAction.SetQuery -> query.value = action.text
            is HomeAction.ShowMenu -> _state.update { it.copy(menuFor = action.uri) }
            HomeAction.HideMenu -> _state.update { it.copy(menuFor = null) }
            is HomeAction.Remove -> remove(action.item)
            HomeAction.UndoRemove -> removed?.let { item -> removed = null; viewModelScope.launch { recents.restore(item) } }
            HomeAction.AskClear -> _state.update { it.copy(confirmClear = true) }
            HomeAction.ConfirmClear -> clear()
            HomeAction.DismissClear -> _state.update { it.copy(confirmClear = false) }
            is HomeAction.Share -> _state.update { it.copy(menuFor = null) }   // the route sends the archive
            is HomeAction.ExtractAll -> openRecent(action.item, extractAll = true)
            HomeAction.NoticeShown -> _state.update { it.copy(notice = null) }
            HomeAction.Navigated -> _state.update { it.copy(navigate = null) }
        }
    }

    private fun openRecent(item: RecentArchive, extractAll: Boolean) {
        _state.update { it.copy(menuFor = null) }
        if (!readCheck.canRead(item.uri)) {
            viewModelScope.launch { recents.remove(item.uri)?.let { permissions.release(it.uri) } }
            _state.update { it.copy(notice = HomeNotice.Gone) }
            return
        }
        val id = session.register(OpenRequest(item.uri, null, null, null, startPath = item.lastPath, extractAll = extractAll))
        navigate(if (extractAll) HomeNav.ExtractAll(id) else HomeNav.Browser(id))
    }

    /** The permission is kept until the undo is no longer possible (the next notice replaces this one). */
    private fun remove(item: RecentArchive) {
        _state.update { it.copy(menuFor = null) }
        viewModelScope.launch {
            removed?.let { permissions.release(it.uri) }
            removed = recents.remove(item.uri)
            removed?.let { gone -> _state.update { it.copy(notice = HomeNotice.Removed(gone)) } }
        }
    }

    private fun clear() {
        _state.update { it.copy(confirmClear = false) }
        viewModelScope.launch { recents.clear().forEach { permissions.release(it.uri) } }
    }

    private fun navigate(nav: HomeNav) = _state.update { it.copy(navigate = nav) }
}
