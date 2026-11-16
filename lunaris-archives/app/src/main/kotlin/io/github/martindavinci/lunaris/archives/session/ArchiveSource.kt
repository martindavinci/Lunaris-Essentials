package io.github.martindavinci.lunaris.archives.session

import android.os.ParcelFileDescriptor
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind

/**
 * An archive the engine can read. [openFd] gives a fresh, read-only, seekable descriptor each
 * time (or one of the incoming copy); [kindHint] is what the first bytes said.
 */
data class ArchiveSource(
    val uri: String, val name: String, val size: Long?, val modifiedMillis: Long?, val key: String,
    val kindHint: ArchiveKind, val openFd: () -> ParcelFileDescriptor,
)
