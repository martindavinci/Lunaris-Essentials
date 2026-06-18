package tools.lunaris.texteditor.feature.browser

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.data.FileFilterStore
import tools.lunaris.texteditor.files.FileEntry
import tools.lunaris.texteditor.files.FileFilter
import tools.lunaris.texteditor.files.SafTreeBrowser
import tools.lunaris.texteditor.files.StorageAccess
import tools.lunaris.texteditor.syntax.LanguageCatalog
import javax.inject.Inject

data class FolderUiState(
    val accessGranted: Boolean = false,
    val location: String? = null,
    val entries: List<FileEntry> = emptyList(),
    val canGoUp: Boolean = false,
    val filterEnabled: Boolean = true,
)

@HiltViewModel
class FolderBrowserViewModel @Inject constructor(
    private val safBrowser: SafTreeBrowser,
    private val storageAccess: StorageAccess,
    private val fileFilterStore: FileFilterStore,
    languageCatalog: LanguageCatalog,
) : ViewModel() {

    private val knownExts = languageCatalog.languages.flatMap { it.extensions }.toSet()
    private var raw: List<FileEntry> = emptyList()
    // The user explicitly closed the folder this session; stay collapsed until they pick one again.
    private var closed = false
    private val _state = MutableStateFlow(FolderUiState())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    /** Initialize/refresh the browser when the drawer opens. */
    fun start() {
        viewModelScope.launch {
            val filter = fileFilterStore.fileFilter().first()
            val rootRef = storageAccess.grantedTrees().firstOrNull()?.toString()
            if (closed || rootRef == null) {
                _state.value = FolderUiState(accessGranted = false, filterEnabled = filter)
            } else {
                loadInto(rootRef, filter)
            }
        }
    }

    /** Collapse the browser back to the empty state (the back arrow at the root folder). */
    fun closeFolder() {
        closed = true
        _state.value = FolderUiState(accessGranted = false, filterEnabled = _state.value.filterEnabled)
    }

    fun navigateTo(ref: String) = viewModelScope.launch { closed = false; loadInto(ref, _state.value.filterEnabled) }

    fun up() = viewModelScope.launch {
        val loc = _state.value.location ?: return@launch
        safBrowser.parent(loc)?.let { loadInto(it, _state.value.filterEnabled) }
    }

    fun toggleFilter(on: Boolean) = viewModelScope.launch {
        fileFilterStore.setFileFilter(on)
        _state.update { it.copy(filterEnabled = on, entries = FileFilter.apply(raw, knownExts, on)) }
    }

    fun onTreePicked(uri: Uri) {
        storageAccess.persistTree(uri)
        navigateTo(uri.toString())
    }

    private suspend fun loadInto(ref: String, filter: Boolean) {
        raw = safBrowser.list(ref)
        _state.value = FolderUiState(
            accessGranted = true,
            location = ref,
            entries = FileFilter.apply(raw, knownExts, filter),
            canGoUp = safBrowser.parent(ref) != null,
            filterEnabled = filter,
        )
    }
}
