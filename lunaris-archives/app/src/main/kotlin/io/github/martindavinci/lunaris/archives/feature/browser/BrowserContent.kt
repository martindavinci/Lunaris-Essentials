package io.github.martindavinci.lunaris.archives.feature.browser

import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.browse.EntrySort
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.browse.FolderView
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import io.github.martindavinci.lunaris.archives.ui.CrumbSegment

/** Pure pieces of the browser state: what a folder shows and how a selection changes. */
internal object BrowserContent {
    fun header(archive: OpenArchive, view: FolderView): ArchiveHeader {
        val files = archive.index.entries.filter { it.type == EntryType.FILE }
        val packed = files.map { it.packedSize }.takeIf { sizes -> sizes.all { it != null } }?.sumOf { it ?: 0 }
        return ArchiveHeader(
            id = archive.id, name = archive.source.name, formatLabel = archive.index.formatLabel,
            fileCount = view.fileCount(), folderCount = view.folderCount(), sizeBytes = files.sumOf { it.size ?: 0 },
            packedBytes = packed, encryption = archive.index.encryption, createdBy = archive.index.createdBy.takeIf { it != HostOs.MSDOS },
            comment = archive.index.comment,
        )
    }

    fun crumbs(archiveName: String, path: List<String>): List<CrumbSegment> =
        listOf(CrumbSegment(archiveName, isArchive = true)) + path.map { CrumbSegment(it, isArchive = false) }

    /** The folder at [state]'s path, sorted; an unknown path falls back to the root. */
    fun showFolder(state: BrowserUiState, view: FolderView, path: List<String>): BrowserUiState {
        val (shown, rows) = view.children(path)?.let { path to it } ?: (emptyList<String>() to view.children(emptyList()).orEmpty())
        return state.copy(path = shown, crumbs = crumbs(state.title, shown), rows = EntrySort.sort(rows, state.sort))
    }

    fun toggle(state: BrowserUiState, row: FolderRow): BrowserUiState {
        var files = state.selection.orEmpty()
        var folders = state.selectedFolders
        if (row.isFolder) {
            folders = if (row.path in folders) folders - setOf(row.path) else folders + setOf(row.path)
        } else {
            val index = row.entry?.index ?: return state
            files = if (index in files) files - index else files + index
        }
        return withSelection(state, files, folders)
    }

    fun selectAll(state: BrowserUiState): BrowserUiState {
        val files = state.selection.orEmpty() + state.rows.filter { !it.isFolder }.mapNotNull { it.entry?.index }
        val folders = state.selectedFolders + state.rows.filter { it.isFolder }.map { it.path }
        return withSelection(state, files, folders)
    }

    fun clearSelection(state: BrowserUiState) = state.copy(selection = null, selectedFolders = emptySet(), canShareSelection = false)

    private fun withSelection(state: BrowserUiState, files: Set<Long>, folders: Set<List<String>>): BrowserUiState =
        state.copy(selection = files, selectedFolders = folders, canShareSelection = folders.isEmpty() && files.isNotEmpty())

    /** Every entry of the rows: a file itself, a folder with everything under it. */
    fun indicesFor(archive: OpenArchive, files: Collection<Long>, folders: Collection<List<String>>): LongArray {
        val chosen = folders.toHashSet()
        val under = if (chosen.isEmpty()) emptyList() else archive.index.entries.filter { entry ->
            val path = (entry.path as? SafePath.Result.Ok)?.components ?: return@filter false
            (0..path.size).any { depth -> path.subList(0, depth) in chosen }   // subList is a view: nothing is copied
        }.map { it.index }
        return (files + under).distinct().sorted().toLongArray()
    }
}
