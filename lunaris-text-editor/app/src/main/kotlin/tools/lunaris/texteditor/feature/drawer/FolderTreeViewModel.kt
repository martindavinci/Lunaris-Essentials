package tools.lunaris.texteditor.feature.drawer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tools.lunaris.texteditor.files.SafTreeBrowser
import tools.lunaris.texteditor.files.StorageAccess
import javax.inject.Inject

data class FolderTreeUiState(
    val accessGranted: Boolean = false,
    val nodes: List<TreeNode> = emptyList(),
)

@HiltViewModel
class FolderTreeViewModel @Inject constructor(
    private val browser: SafTreeBrowser,
    private val storageAccess: StorageAccess,
) : ViewModel() {

    private var tree = FolderTree(emptyList())
    private val mutex = Mutex()
    private val _state = MutableStateFlow(FolderTreeUiState())
    val state: StateFlow<FolderTreeUiState> = _state.asStateFlow()

    /** Load granted roots and pre-count their children (badges show while collapsed). */
    fun start() = viewModelScope.launch {
        mutex.withLock { loadRoots() }
    }

    fun toggle(ref: String) = viewModelScope.launch {
        mutex.withLock {
            tree = tree.toggle(ref)
            if (ref in tree.expanded && tree.children[ref] == null) {
                tree = tree.withChildren(ref, browser.list(ref))
            }
            publish()
        }
    }

    fun pickTree(uri: Uri) {
        storageAccess.persistTree(uri)
        viewModelScope.launch { mutex.withLock { loadRoots() } }
    }

    fun closeRoot(ref: String) = viewModelScope.launch {
        mutex.withLock {
            storageAccess.releaseTree(Uri.parse(ref))
            loadRoots()
        }
    }

    fun createFile(parentRef: String, name: String) = viewModelScope.launch {
        mutex.withLock {
            browser.createFile(parentRef, name)
            tree = tree.copy(expanded = tree.expanded + parentRef)
            reload(parentRef)
        }
    }

    fun createFolder(parentRef: String, name: String) = viewModelScope.launch {
        mutex.withLock {
            browser.createFolder(parentRef, name)
            tree = tree.copy(expanded = tree.expanded + parentRef)
            reload(parentRef)
        }
    }

    fun rename(ref: String, newName: String) = viewModelScope.launch {
        mutex.withLock {
            browser.rename(ref, newName)
            // A renamed root/child changes a listing → reload from the top for correctness.
            loadRoots()
        }
    }

    /** Rebuild the tree from granted roots, preserving expansion and reloading expanded folders' children. */
    private suspend fun loadRoots() {
        val roots = storageAccess.grantedTrees().map { RootRef(it.toString(), prettyLocation(it.toString())) }
        val keepExpanded = tree.expanded
        var t = FolderTree(roots, expanded = keepExpanded)
        (roots.map { it.ref } + keepExpanded).distinct().forEach { ref -> t = t.withChildren(ref, browser.list(ref)) }
        tree = t
        publish()
    }

    /** Re-list one folder's children in place (after create). */
    private suspend fun reload(ref: String) {
        tree = tree.withChildren(ref, browser.list(ref))
        publish()
    }

    private fun publish() {
        _state.value = FolderTreeUiState(accessGranted = tree.roots.isNotEmpty(), nodes = tree.flatten())
    }
}

/** Turns a SAF tree/document uri into a readable path, e.g. "Download/notes". */
internal fun prettyLocation(uri: String): String {
    val decoded = runCatching { java.net.URLDecoder.decode(uri, "UTF-8") }.getOrDefault(uri)
    val tail = decoded.substringAfterLast(':').trim('/')
    return tail.substringAfterLast('/').ifBlank { tail.ifBlank { "/" } }
}
