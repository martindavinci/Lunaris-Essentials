package io.github.martindavinci.lunaris.archives.browse

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.safety.SafePath

/** One row of a folder: a folder (declared by an entry or implied by a path) or a file. */
data class FolderRow(
    val name: String, val path: List<String>, val isFolder: Boolean, val entry: ArchiveEntry?,
    val childCount: Int, val totalSize: Long,
)

/**
 * Folders built once from the flat index. Entries the path rules refuse (outside the folder,
 * invalid names, links, devices) are not placed, only counted in [hiddenUnsafe]. Rows keep the
 * order of the archive; sorting is [EntrySort]'s job.
 */
class FolderView(index: ArchiveIndex) {
    private class Node(val name: String) {
        val folders = LinkedHashMap<String, Node>()
        val files = ArrayList<ArchiveEntry>()
        var declared: ArchiveEntry? = null
        var totalSize = 0L
    }

    private val root = Node("")
    private var folders = 0

    val hiddenUnsafe: Int

    init {
        var hidden = 0
        for (entry in index.entries) {
            val path = entry.path
            if (path !is SafePath.Result.Ok) {
                hidden++
                continue
            }
            if (entry.type == EntryType.DIRECTORY) {
                folderAt(path.components).declared = entry
            } else {
                folderAt(path.components.dropLast(1)).files += entry
            }
        }
        hiddenUnsafe = hidden
        sumSizes(root)
    }

    fun children(path: List<String>): List<FolderRow>? {
        val node = find(path) ?: return null
        val folderRows = node.folders.values.map { child ->
            FolderRow(child.name, path + child.name, true, child.declared, child.folders.size + child.files.size, child.totalSize)
        }
        val fileRows = node.files.map { file ->
            FolderRow(file.path.leafName(), path + file.path.leafName(), false, file, 0, file.size ?: 0)
        }
        return folderRows + fileRows
    }

    fun entriesUnder(path: List<String>): List<ArchiveEntry> {
        val node = find(path) ?: return emptyList()
        val out = ArrayList<ArchiveEntry>()
        collect(node, out)
        return out
    }

    fun folderCount(): Int = folders

    fun fileCount(): Int = countFiles(root)

    private fun folderAt(components: List<String>): Node {
        var node = root
        for (name in components) {
            node = node.folders.getOrPut(name) { folders++; Node(name) }
        }
        return node
    }

    private fun find(path: List<String>): Node? {
        var node = root
        for (name in path) node = node.folders[name] ?: return null
        return node
    }

    private fun sumSizes(node: Node): Long {
        node.totalSize = node.files.sumOf { it.size ?: 0 } + node.folders.values.sumOf(::sumSizes)
        return node.totalSize
    }

    private fun collect(node: Node, out: MutableList<ArchiveEntry>) {
        out += node.files
        node.folders.values.forEach { collect(it, out) }
    }

    private fun countFiles(node: Node): Int = node.files.size + node.folders.values.sumOf(::countFiles)

    private fun SafePath.Result.leafName(): String = (this as SafePath.Result.Ok).components.last()
}
