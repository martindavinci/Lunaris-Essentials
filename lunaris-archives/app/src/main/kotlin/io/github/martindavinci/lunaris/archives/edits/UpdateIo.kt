package io.github.martindavinci.lunaris.archives.edits

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/** Reading and replacing the archive being updated (ContentResolver on the device). */
interface UpdateIo {
    fun openInput(uri: String): InputStream

    /** Null when the archive can no longer be read. */
    fun facts(uri: String): ArchiveFacts?

    /** Opens for read and write without changing anything ("rw", never "w": some providers empty the file on "w"). */
    fun canWrite(uri: String): Boolean

    /** Keeps the right to write across a restart, for the recovery; true when it was taken here (and is to be given back). */
    fun keepAccess(uri: String): Boolean

    fun releaseAccess(uri: String)

    /** The archive emptied and open for its new bytes. */
    fun openForReplace(uri: String): ReplaceTarget
}

interface ReplaceTarget : Closeable {
    val stream: OutputStream

    /** Cuts the file where the writing stopped: some providers ignore the "t" of "rwt". */
    fun truncateHere()

    /** Asks for the bytes to reach the disk; providers that cannot do it are not an error. */
    fun sync()
}

sealed interface UpdateResult {
    data class Done(val newSize: Long, val newMtime: Long?, val fingerprint: String) : UpdateResult
    data object NoSpace : UpdateResult
    data object ArchiveChanged : UpdateResult
    data object NotWritable : UpdateResult
    data object NeedsPassword : UpdateResult
    data object WrongPassword : UpdateResult
    /** An entry compressed or encrypted in a way this app cannot write back. */
    data object Unsupported : UpdateResult
    /** Nothing changed: the original is as it was. */
    data object Failed : UpdateResult
    /** The write failed and so did putting the original back: its copy is kept at [backupPath]. */
    data class RestoreFailed(val backupPath: String) : UpdateResult
}
