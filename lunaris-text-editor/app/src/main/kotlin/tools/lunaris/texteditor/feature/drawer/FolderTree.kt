package tools.lunaris.texteditor.feature.drawer

import tools.lunaris.texteditor.files.FileEntry

data class RootRef(val ref: String, val name: String)

data class TreeNode(
    val ref: String,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int,
    val expanded: Boolean,
    val childCount: Int?,
)

/** Immutable folder tree. [children] holds the loaded entries per folder ref; a folder with no
 *  entry in the map is simply not loaded yet. [flatten] walks roots depth-first, descending only
 *  into expanded folders whose children are loaded. */
data class FolderTree(
    val roots: List<RootRef>,
    val expanded: Set<String> = emptySet(),
    val children: Map<String, List<FileEntry>> = emptyMap(),
) {
    fun toggle(ref: String): FolderTree =
        copy(expanded = if (ref in expanded) expanded - ref else expanded + ref)

    fun withChildren(ref: String, entries: List<FileEntry>): FolderTree =
        copy(children = children + (ref to entries))

    fun withRoots(newRoots: List<RootRef>): FolderTree = copy(roots = newRoots)

    fun flatten(): List<TreeNode> {
        val out = ArrayList<TreeNode>()
        roots.forEach { root ->
            out += node(root.ref, root.name, isDirectory = true, depth = 0)
            if (root.ref in expanded) descend(root.ref, depth = 1, out)
        }
        return out
    }

    private fun descend(ref: String, depth: Int, out: ArrayList<TreeNode>) {
        val kids = children[ref] ?: return
        kids.forEach { e ->
            out += node(e.ref, e.name, e.isDirectory, depth)
            if (e.isDirectory && e.ref in expanded) descend(e.ref, depth + 1, out)
        }
    }

    private fun node(ref: String, name: String, isDirectory: Boolean, depth: Int) = TreeNode(
        ref = ref,
        name = name,
        isDirectory = isDirectory,
        depth = depth,
        expanded = ref in expanded,
        childCount = children[ref]?.size,
    )
}
