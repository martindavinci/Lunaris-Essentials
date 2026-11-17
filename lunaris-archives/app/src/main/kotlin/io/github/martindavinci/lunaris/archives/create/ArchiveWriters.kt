package io.github.martindavinci.lunaris.archives.create

import io.github.martindavinci.lunaris.archives.di.CacheDir
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.session.FreeBytes
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/** Opens the writer of a format over the "Save as" stream. */
fun interface WriterFactory {
    fun open(
        format: ArchiveFormat, level: CompressionLevel, password: CharArray?, out: OutputStream, totalBytes: Long,
        onFinishing: (copied: Long, total: Long) -> Unit,
    ): ArchiveWriter
}

/** ZIP and TAR stream straight out; a 7z first goes to cache/create, so it needs room there. */
class ArchiveWriters @Inject constructor(@param:CacheDir cacheDir: File, private val freeBytes: FreeBytes) : WriterFactory {
    private val workDir = File(cacheDir, FOLDER)

    override fun open(
        format: ArchiveFormat, level: CompressionLevel, password: CharArray?, out: OutputStream, totalBytes: Long,
        onFinishing: (copied: Long, total: Long) -> Unit,
    ): ArchiveWriter = when (format) {
        ArchiveFormat.ZIP -> ZipWriter(out, level, password)
        ArchiveFormat.SEVEN_Z -> {
            if (freeBytes.get() < totalBytes + Limits.CACHE_MARGIN_BYTES) throw IOException(NO_SPACE)
            SevenZWriter(out, level, workDir, onFinishing)
        }
        else -> TarWriter(out, format, workDir)
    }

    private companion object {
        const val FOLDER = "create"
        /** Matched by FreeSpace.isNoSpace, like the system's own message. */
        const val NO_SPACE = "No space left in the cache for the 7z work file"
    }
}
