package io.github.martindavinci.lunaris.archives.create

import io.github.martindavinci.lunaris.archives.extract.KeepBothNamer
import io.github.martindavinci.lunaris.archives.safety.SafeComponent
import java.util.Locale

/** One card of the "New archive" screen: picked files, or one picked folder. */
data class CreateItem(val id: String, val name: String, val isFolder: Boolean, val fileCount: Int, val sizeBytes: Long?)

/** A file that goes into the ZIP, and its path there. */
data class SourceFile(val uri: String, val pathInZip: String, val sizeBytes: Long?, val modifiedMillis: Long?)

data class Described(val name: String, val sizeBytes: Long?, val modifiedMillis: Long?, val isVirtual: Boolean)

data class Child(
    val documentId: String, val name: String, val isFolder: Boolean, val sizeBytes: Long?, val modifiedMillis: Long?, val isVirtual: Boolean,
)

/** The documents the user picked (DocumentsContract on the device). */
interface SourceTree {
    /** Name, size, date of one picked file; null when it is gone. */
    fun describe(uri: String): Described?
    /** The root document id and the folder's name of a picked tree; null when it is gone. */
    fun rootOf(treeUri: String): Pair<String, String>?
    fun children(treeUri: String, documentId: String): List<Child>
    fun uriOf(treeUri: String, documentId: String): String
}

/** Turns picks into files with their paths in the ZIP. Virtual documents (no bytes of their own) and gone files are left out. */
class CreateSources(private val tree: SourceTree) {
    private val left = ArrayList<String>()
    val skipped: List<String> get() = left

    fun fromFiles(uris: List<String>): List<SourceFile> {
        val found = uris.mapNotNull { uri ->
            val described = tree.describe(uri)
            when {
                described == null -> null.also { left += uri }
                described.isVirtual -> null.also { left += described.name }
                else -> SourceFile(uri, described.name, described.sizeBytes, described.modifiedMillis)
            }
        }
        return withUniquePaths(found)
    }

    /** The folder's name and its files under "Folder/…", folder by folder (breadth first). */
    fun fromFolder(treeUri: String): Pair<String, List<SourceFile>> {
        val (rootId, name) = tree.rootOf(treeUri) ?: return ("" to emptyList<SourceFile>()).also { left += treeUri }
        val files = ArrayList<SourceFile>()
        val queue = ArrayDeque(listOf(rootId to name))
        while (queue.isNotEmpty()) {
            val (id, prefix) = queue.removeFirst()
            for (child in tree.children(treeUri, id)) {
                val path = "$prefix$SEPARATOR${child.name}"
                when {
                    child.isFolder -> queue.addLast(child.documentId to path)
                    child.isVirtual -> left += child.name
                    else -> files += SourceFile(tree.uriOf(treeUri, child.documentId), path, child.sizeBytes, child.modifiedMillis)
                }
            }
        }
        return name to withUniquePaths(files)
    }

    fun item(files: List<SourceFile>, name: String, isFolder: Boolean): CreateItem {
        val sizes = files.map { it.sizeBytes }
        val total = if (sizes.all { it != null }) sizes.sumOf { it ?: 0 } else null
        return CreateItem(files.firstOrNull()?.uri ?: name, name, isFolder, files.size, total)
    }

    private fun withUniquePaths(files: List<SourceFile>): List<SourceFile> =
        files.zip(ZipPaths.unique(files.map { it.pathInZip })) { file, path -> file.copy(pathInZip = path) }

    private companion object { const val SEPARATOR = "/" }
}

/** Paths inside a new ZIP: every component writable (NFC, no "\" or ":"), no two equal ignoring case. */
object ZipPaths {
    private const val SEPARATOR = "/"
    private const val FALLBACK = "file"

    fun unique(paths: List<String>): List<String> {
        val taken = HashMap<String, MutableSet<String>>()
        return paths.map { path ->
            val parts = path.split(SEPARATOR).filter { it.isNotEmpty() }.map { SafeComponent.forWriting(it) ?: FALLBACK }
            val folder = parts.dropLast(1).joinToString(SEPARATOR)
            val inFolder = taken.getOrPut(folder.lowercase(Locale.ROOT)) { HashSet() }
            val leaf = parts.lastOrNull() ?: FALLBACK
            val name = if (leaf.lowercase(Locale.ROOT) in inFolder) KeepBothNamer.name(leaf, inFolder) else leaf
            inFolder += name.lowercase(Locale.ROOT)
            if (folder.isEmpty()) name else "$folder$SEPARATOR$name"
        }
    }
}
