package io.github.martindavinci.lunaris.archives.edits

/** A copy of a changed file, kept in filesDir so that clearing the cache cannot lose it. */
data class Snapshot(val path: String, val size: Long, val mtime: Long, val sha256: String)

/**
 * One copy handed to another app with the right to write: where it is, which archive and entry it came from, and
 * its bytes when handed out ([size], [mtime], [sha256]: the baseline). [pending] is set once a change was seen.
 * No password is ever part of it.
 */
data class EditRecord(
    val id: String,
    val copyPath: String,
    val archiveUri: String,
    /** The session key of the archive (not secret): finds a remembered password. */
    val archiveKey: String,
    val archiveName: String,
    val archiveSize: Long,
    val archiveMtime: Long?,
    val archiveFingerprint: String,
    val archiveFormat: String,
    /** The path as the screens show it (for the banner). */
    val entryPath: String,
    val size: Long,
    val mtime: Long,
    val sha256: String,
    val updatable: Boolean,
    val createdMillis: Long,
    val pending: Snapshot? = null,
    /** The entry name exactly as stored in the archive, in hex: an update finds the entry by these bytes. */
    val entryRaw: String = "",
)
