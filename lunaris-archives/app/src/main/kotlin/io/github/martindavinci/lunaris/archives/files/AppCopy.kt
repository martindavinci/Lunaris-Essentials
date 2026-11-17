package io.github.martindavinci.lunaris.archives.files

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import java.io.File

/** A copy for another app, and whether that app may write to it (its changes can then come back). */
data class AppCopy(val result: CopyResult, val writable: Boolean)

/** Read-only and write modes of the copies other apps get: everything that replaces a file whole. */
object ProviderModes {
    private val ALLOWED = setOf("r", "w", "wt", "rw", "rwt")

    fun allows(mode: String): Boolean = mode in ALLOWED
}

/** The file to hand out instead of a fresh copy when the copy at [copy] carries an edit; null when there is none. */
fun interface EditedCopies {
    suspend fun edited(copy: File): File?

    companion object {
        val NONE = EditedCopies { null }
    }
}

/** Remembers a copy handed out for editing; false when it must stay read-only. */
fun interface CopyRecorder {
    suspend fun record(archive: OpenArchive, entry: ArchiveEntry, copy: File): Boolean

    companion object {
        val NONE = CopyRecorder { _, _, _ -> false }
    }
}
