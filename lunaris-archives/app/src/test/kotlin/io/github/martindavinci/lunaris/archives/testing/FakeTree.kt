package io.github.martindavinci.lunaris.archives.testing

import io.github.martindavinci.lunaris.archives.extract.Created
import io.github.martindavinci.lunaris.archives.extract.ExistingNames
import io.github.martindavinci.lunaris.archives.extract.FolderGoneException
import io.github.martindavinci.lunaris.archives.extract.SafTarget
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

/**
 * An in-memory SAF tree. Folders and files are kept apart like a real provider: a file is never
 * returned for a folder name. Names keep their case; lookups ignore it.
 */
class FakeTree(
    private val appendsExtension: Boolean = false,
    private val free: Long? = null,
    private val goneAfterWrites: Int? = null,
    private val failWithNoSpaceAfterBytes: Long? = null,
) : SafTarget {
    val folders = mutableSetOf<List<String>>(emptyList())
    val files = mutableMapOf<List<String>, MutableMap<String, ByteArray>>()
    private var opened = 0
    private var written = 0L

    override val label: String? = "Download"

    override fun freeBytes(): Long? = free

    override fun children(folder: List<String>): ExistingNames? {
        val path = find(folder) ?: return null
        val subfolders = folders.filter { it.size == path.size + 1 && it.take(path.size) == path }.map { lower(it.last()) }.toSet()
        return ExistingNames(files[path].orEmpty().keys.map(::lower).toSet(), subfolders)
    }

    override fun ensureFolder(folder: List<String>): String {
        var path = emptyList<String>()
        for (name in folder) {
            val existing = folders.firstOrNull { it.size == path.size + 1 && it.take(path.size) == path && lower(it.last()) == lower(name) }
            path = existing ?: (path + name).also { folders += it }
        }
        return token(path)
    }

    override fun createFile(folderToken: String, name: String): Created {
        val folder = path(folderToken)
        val inFolder = files.getOrPut(folder) { mutableMapOf() }
        var actual = if (appendsExtension) "$name.bin" else name
        var n = 1
        while (inFolder.keys.any { lower(it) == lower(actual) } || isFolder(folder + actual)) actual = "$name ($n)".also { n++ }
        inFolder[actual] = ByteArray(0)
        return Created(token(folder + actual), actual)
    }

    override fun replace(folderToken: String, name: String): Created? {
        val folder = path(folderToken)
        val actual = files[folder]?.keys?.firstOrNull { lower(it) == lower(name) } ?: return null
        return Created(token(folder + actual), actual, existed = true)
    }

    override fun open(created: Created): OutputStream {
        opened++
        goneAfterWrites?.let { if (opened > it) throw FolderGoneException() }
        val path = path(created.token)
        return object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                failWithNoSpaceAfterBytes?.let { if (written + len > it) throw IOException("ENOSPC") }
                written += len
                super.write(b, off, len)
            }
            override fun close() {
                files.getOrPut(path.dropLast(1)) { mutableMapOf() }[path.last()] = toByteArray()
            }
        }
    }

    override fun delete(created: Created) {
        val path = path(created.token)
        files[path.dropLast(1)]?.remove(path.last())
    }

    fun read(vararg path: String): ByteArray? = files[path.dropLast(1)]?.get(path.last())

    private fun isFolder(path: List<String>) = folders.any { it.size == path.size && it.zip(path).all { (a, b) -> lower(a) == lower(b) } }
    private fun find(folder: List<String>) = folders.firstOrNull { it.size == folder.size && it.zip(folder).all { (a, b) -> lower(a) == lower(b) } }
    private fun token(path: List<String>) = "tree:" + path.joinToString(SEPARATOR)
    private fun path(token: String) = token.removePrefix("tree:").split(SEPARATOR).filter { it.isNotEmpty() }
    private fun lower(name: String) = name.lowercase(Locale.ROOT)

    private companion object { const val SEPARATOR = "/" }
}
