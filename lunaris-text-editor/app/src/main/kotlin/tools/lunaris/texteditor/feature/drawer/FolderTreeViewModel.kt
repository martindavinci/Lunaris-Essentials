package tools.lunaris.texteditor.feature.drawer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val _state = MutableStateFlow(FolderTreeUiState())
    val state: StateFlow<FolderTreeUiState> = _state.asStateFlow()

    /** Load granted roots and pre-count their children (badges show while collapsed). */
    fun start() = viewModelScope.launch {
        val roots = storageAccess.grantedTrees().map { RootRef(it.toString(), prettyLocation(it.toString())) }
        var t = FolderTree(roots)
        roots.forEach { t = t.withChildren(it.ref, browser.list(it.ref)) }
        tree = t
        publish(roots.isNotEmpty())
    }

    fun toggle(ref: String) = viewModelScope.launch {
        tree = tree.toggle(ref)
        if (ref in tree.expanded && tree.children[ref] == null) {
            tree = tree.withChildren(ref, browser.list(ref))
        }
        publish(tree.roots.isNotEmpty())
    }

    fun pickTree(uri: Uri) {
        storageAccess.persistTree(uri)
        start()
    }

    fun closeRoot(ref: String) = viewModelScope.launch {
        storageAccess.releaseTree(Uri.parse(ref))
        start()
    }

    fun createFile(parentRef: String, name: String) = viewModelScope.launch {
        browser.createFile(parentRef, name); reload(parentRef)
    }

    fun createFolder(parentRef: String, name: String) = viewModelScope.launch {
        browser.createFolder(parentRef, name); reload(parentRef)
    }

    fun rename(ref: String, newName: String) = viewModelScope.launch {
        browser.rename(ref, newName)
        // A renamed root/child changes a listing → reload from the top for correctness.
        start()
    }

    /** Re-list one folder's children in place (after create). */
    private suspend fun reload(ref: String) {
        tree = tree.withChildren(ref, browser.list(ref))
        publish(tree.roots.isNotEmpty())
    }

    private fun publish(granted: Boolean) {
        _state.value = FolderTreeUiState(accessGranted = granted, nodes = tree.flatten())
    }
}

/** Turns a SAF tree/document uri into a readable path, e.g. "Download/notes". */
internal fun prettyLocation(uri: String): String {
    val decoded = runCatching { java.net.URLDecoder.decode(uri, "UTF-8") }.getOrDefault(uri)
    val tail = decoded.substringAfterLast(':').trim('/')
    return tail.substringAfterLast('/').ifBlank { tail.ifBlank { "/" } }
}
