package tools.lunaris.texteditor.feature.browser

import android.content.Intent
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
import tools.lunaris.texteditor.files.AllFilesBrowser
import tools.lunaris.texteditor.files.FileEntry
import tools.lunaris.texteditor.files.FileFilter
import tools.lunaris.texteditor.files.FolderBrowser
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
    private val allFilesBrowser: AllFilesBrowser,
    private val storageAccess: StorageAccess,
    private val fileFilterStore: FileFilterStore,
    languageCatalog: LanguageCatalog,
) : ViewModel() {

    private val knownExts = languageCatalog.languages.flatMap { it.extensions }.toSet()
    private var raw: List<FileEntry> = emptyList()
    private val _state = MutableStateFlow(FolderUiState())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    private fun browser(): FolderBrowser = if (storageAccess.hasAllFiles()) allFilesBrowser else safBrowser

    /** Initialize/refresh the browser when the drawer opens. */
    fun start() {
        viewModelScope.launch {
            val filter = fileFilterStore.fileFilter().first()
            val rootRef = when {
                storageAccess.hasAllFiles() -> storageAccess.externalRoot()
                storageAccess.grantedTrees().isNotEmpty() -> storageAccess.grantedTrees().first().toString()
                else -> null
            }
            if (rootRef == null) {
                _state.value = FolderUiState(accessGranted = false, filterEnabled = filter)
            } else {
                loadInto(rootRef, filter)
            }
        }
    }

    fun navigateTo(ref: String) = viewModelScope.launch { loadInto(ref, _state.value.filterEnabled) }

    fun up() = viewModelScope.launch {
        val loc = _state.value.location ?: return@launch
        browser().parent(loc)?.let { loadInto(it, _state.value.filterEnabled) }
    }

    fun toggleFilter(on: Boolean) = viewModelScope.launch {
        fileFilterStore.setFileFilter(on)
        _state.update { it.copy(filterEnabled = on, entries = FileFilter.apply(raw, knownExts, on)) }
    }

    fun onTreePicked(uri: Uri) {
        storageAccess.persistTree(uri)
        navigateTo(uri.toString())
    }

    fun allFilesIntent(): Intent = storageAccess.allFilesSettingsIntent()

    private suspend fun loadInto(ref: String, filter: Boolean) {
        val b = browser()
        raw = b.list(ref)
        _state.value = FolderUiState(
            accessGranted = true,
            location = ref,
            entries = FileFilter.apply(raw, knownExts, filter),
            canGoUp = b.parent(ref) != null,
            filterEnabled = filter,
        )
    }
}
