package io.github.martindavinci.lunaris.archives.session

import android.os.ParcelFileDescriptor
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind

/**
 * An archive the engine can read. [openFd] gives a fresh, read-only, seekable descriptor each
 * time (or one of the incoming copy); [kindHint] is what the first bytes said.
 */
data class ArchiveSource(
    val uri: String, val name: String, val size: Long?, val modifiedMillis: Long?, val key: String,
    val kindHint: ArchiveKind,
    /** A copy in cache/incoming of an archive that could not be read in place: its listing is not saved. */
    val fromCopy: Boolean = false,
    val openFd: () -> ParcelFileDescriptor,
)
