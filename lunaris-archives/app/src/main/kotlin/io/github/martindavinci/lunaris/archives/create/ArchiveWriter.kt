package io.github.martindavinci.lunaris.archives.create

import java.io.Closeable
import java.io.InputStream

/** Adds files to a new archive, one after the other; closing finishes it. */
interface ArchiveWriter : Closeable {
    /** [onBytes] gets the bytes read from [input] so far; it may throw to stop (cancellation). */
    fun add(path: String, sizeBytes: Long?, modifiedMillis: Long?, input: InputStream, onBytes: (Long) -> Unit)
}
