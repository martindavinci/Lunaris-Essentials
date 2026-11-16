package io.github.martindavinci.lunaris.archives.feature.browser

import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.browse.FolderView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The folder on screen. The tree of 200 000 entries and the sorted rows of a folder are built on
 * [background], never on the main thread; when the user moves faster than that, the newest move wins.
 */
internal class BrowserFolders(
    private val scope: CoroutineScope,
    private val background: CoroutineDispatcher,
    private val state: MutableStateFlow<BrowserUiState>,
    private val onShown: (List<String>) -> Unit,
) {
    var view: FolderView? = null
        private set
    private var job: Job? = null

    suspend fun load(index: ArchiveIndex): FolderView = withContext(background) { FolderView(index) }.also { view = it }

    /** Shows [path] (the root when it is not there) with the sort in the state. */
    fun show(path: List<String>) {
        val folders = view ?: return
        job?.cancel()
        job = scope.launch {
            val snapshot = state.value
            val shown = withContext(background) { BrowserContent.showFolder(snapshot, folders, path) }
            state.update { it.copy(path = shown.path, crumbs = shown.crumbs, rows = shown.rows) }
            onShown(shown.path)
        }
    }
}
