package io.github.martindavinci.lunaris.archives.session

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.martindavinci.lunaris.archives.archive.ArchiveSignatures
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.text.withVisibleBidiControls
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SourceResult {
    data class Ready(val source: ArchiveSource) : SourceResult
    data object Gone : SourceResult
    data class NeedsCopy(val uri: Uri, val bytes: Long?) : SourceResult
}

/** Not enough room in the cache for a copy of the archive. */
class NoSpaceException(val neededBytes: Long, val freeBytes: Long) : IOException("no space")

/** Free bytes the cache may still use (StorageManager.getAllocatableBytes on the device). */
fun interface FreeBytes {
    fun get(): Long
}

/** Archives from any provider: read in place when the descriptor is seekable, else copied once. */
@Singleton
class ArchiveSources @Inject constructor(
    private val resolver: ContentResolver,
    private val incoming: IncomingCopy,
    private val freeBytes: FreeBytes,
) {
    suspend fun open(uri: Uri): SourceResult = withContext(Dispatchers.IO) {
        val (name, size, modified) = describe(uri)
        val fd = try {
            resolver.openFileDescriptor(uri, READ) ?: return@withContext SourceResult.Gone
        } catch (_: SecurityException) {
            return@withContext SourceResult.Gone
        } catch (_: FileNotFoundException) {
            return@withContext SourceResult.Gone
        }
        val head = fd.use { if (seekable(it)) head(it) else null } ?: return@withContext SourceResult.NeedsCopy(uri, size)
        val key = ArchiveKeys.of(uri.toString(), size, modified)
        SourceResult.Ready(
            ArchiveSource(uri.toString(), name, size, modified, key, ArchiveSignatures.detect(head, name)) {
                resolver.openFileDescriptor(uri, READ) ?: throw FileNotFoundException(uri.toString())
            },
        )
    }

    /** For incoming and nested copies: a file of our own cache. */
    fun fromFile(
        file: File, name: String, uri: String = Uri.fromFile(file).toString(),
        key: String = ArchiveKeys.of(uri, file.length(), file.lastModified()),
    ): ArchiveSource {
        val head = file.inputStream().use { input -> ByteArray(ArchiveSignatures.ISO_PROBE_END).let { it.copyOf(maxOf(0, input.read(it))) } }
        return ArchiveSource(uri, name, file.length(), file.lastModified(), key, ArchiveSignatures.detect(head, name)) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    /** Copies a non-seekable archive into the cache, after checking there is room for it. */
    suspend fun copyIn(uri: Uri, bytes: Long?, onProgress: (Long) -> Unit): ArchiveSource {
        val needed = (bytes ?: 0) + Limits.CACHE_MARGIN_BYTES
        val free = freeBytes.get()
        if (free < needed) throw NoSpaceException(needed, free)
        val (name, size, modified) = describe(uri)
        val key = ArchiveKeys.of(uri.toString(), size, modified)
        val input = withContext(Dispatchers.IO) { resolver.openInputStream(uri) } ?: throw FileNotFoundException(uri.toString())
        val file = incoming.copy(input, incoming.target(key, name), onProgress)
        return fromFile(file, name, uri.toString(), key)
    }

    private data class Description(val name: String, val size: Long?, val modified: Long?)

    private fun describe(uri: Uri): Description {
        var name = uri.lastPathSegment ?: FALLBACK_NAME
        var size: Long? = null
        var modified: Long? = null
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                    c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        .takeIf { it >= 0 && !c.isNull(it) }?.let { modified = c.getLong(it) }
                }
            }
        }
        return Description(name.withVisibleBidiControls(), size, modified)
    }

    private fun seekable(fd: ParcelFileDescriptor): Boolean = try {
        OsConstants.S_ISREG(Os.fstat(fd.fileDescriptor).st_mode) && Os.lseek(fd.fileDescriptor, 0, OsConstants.SEEK_CUR) >= 0
    } catch (_: ErrnoException) {
        false
    }

    /** The first bytes, read with pread so the descriptor's offset does not move. */
    private fun head(fd: ParcelFileDescriptor): ByteArray? = try {
        val buffer = ByteArray(ArchiveSignatures.ISO_PROBE_END)
        val n = Os.pread(fd.fileDescriptor, buffer, 0, buffer.size, 0)
        buffer.copyOf(maxOf(0, n))
    } catch (_: ErrnoException) {
        null
    }

    private companion object {
        const val READ = "r"
        const val FALLBACK_NAME = "archive"
    }
}
