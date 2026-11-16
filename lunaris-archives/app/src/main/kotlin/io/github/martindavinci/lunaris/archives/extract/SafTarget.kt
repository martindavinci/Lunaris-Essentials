package io.github.martindavinci.lunaris.archives.extract

import java.io.IOException
import java.io.OutputStream

/** A document of the destination. [token] is its document URI as text, so a restart can delete a half-written one. */
data class Created(val token: String, val name: String, val existed: Boolean = false)

class FolderGoneException : IOException("folder gone")

/** The provider would not open an existing file for a truncating write. */
class CannotTruncateException : IOException("cannot truncate")

/** The destination tree, small enough to fake in JVM tests (FakeTree). */
interface SafTarget {
    /** The children of an existing folder, read once per folder; null if the folder does not exist. */
    fun children(folder: List<String>): ExistingNames?

    /** Creates (or finds, ignoring case) every folder of the path; returns a token for the last one. */
    fun ensureFolder(folder: List<String>): String

    /** Creates a file (MIME application/octet-stream, so no provider adds an extension). Never overwrites. */
    fun createFile(folderToken: String, name: String): Created

    /** An existing FILE of that name, to be truncated; null when there is none. A folder is never returned. */
    fun replace(folderToken: String, name: String): Created?

    /** For a [Created] that existed: truncates it, or throws [CannotTruncateException]. */
    fun open(created: Created): OutputStream

    fun delete(created: Created)

    /** Free bytes where the files go; null when it cannot be known (not a local file). */
    fun freeBytes(): Long?

    /** "Documents / Project", or null. */
    val label: String?
}
