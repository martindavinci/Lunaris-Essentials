package io.github.martindavinci.lunaris.archives.create

import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

/**
 * A TAR, plain or packed with gzip, xz or bzip2. A TAR header carries the size before the data, so every file is
 * read first (in memory when small, else into [spoolDir]): the header always says what was really written.
 * No user or group name goes in: the archive says nothing about the phone it was made on.
 */
class TarWriter(out: OutputStream, format: ArchiveFormat, private val spoolDir: File) : ArchiveWriter {
    private val tar: TarArchiveOutputStream

    init {
        require(format.isTar) { "not a tar format: $format" }
        val buffered = BufferedOutputStream(out, Limits.WIRE_CHUNK_BYTES)
        val packed = when (format) {
            ArchiveFormat.TAR_GZ -> GzipCompressorOutputStream(buffered, GzipParameters().apply { compressionLevel = GZIP_LEVEL })
            ArchiveFormat.TAR_XZ -> XZCompressorOutputStream(buffered, XZ_PRESET)
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorOutputStream(buffered, BZIP2_BLOCK)
            else -> buffered
        }
        tar = TarArchiveOutputStream(packed, UTF_8).apply {
            setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            setAddPaxHeadersForNonAsciiNames(true)
        }
    }

    override fun add(path: String, sizeBytes: Long?, modifiedMillis: Long?, input: InputStream, onBytes: (Long) -> Unit) {
        input.use { source -> Spool.of(source, spoolDir, onBytes) }.use { spool ->
            val entry = TarArchiveEntry(path).apply {
                size = spool.length
                modTime = Date(modifiedMillis ?: System.currentTimeMillis())
                mode = TarArchiveEntry.DEFAULT_FILE_MODE
                userName = ""
                groupName = ""
                setUserId(0L)
                setGroupId(0L)
            }
            tar.putArchiveEntry(entry)
            spool.open().use { it.copyTo(tar, Limits.WIRE_CHUNK_BYTES) }
            tar.closeArchiveEntry()
        }
    }

    override fun close() {
        tar.close()
    }

    /** One file read whole: in memory up to [Limits.WIRE_CHUNK_BYTES], else in a temp file deleted on close. */
    private class Spool(private val memory: ByteArray?, private val file: File?, val length: Long) : AutoCloseable {
        fun open(): InputStream = memory?.let { ByteArrayInputStream(it) } ?: checkNotNull(file).inputStream()

        override fun close() {
            file?.delete()
        }

        companion object {
            fun of(source: InputStream, dir: File, onBytes: (Long) -> Unit): Spool {
                val buffer = ByteArray(Limits.WIRE_CHUNK_BYTES)
                val head = ByteArrayOutputStream()
                var total = 0L
                var file: File? = null
                var out: OutputStream = head
                try {
                    while (true) {
                        val n = try {
                            source.read(buffer)
                        } catch (e: IOException) {
                            throw SourceReadException(e)
                        }
                        if (n < 0) break
                        if (file == null && total + n > Limits.WIRE_CHUNK_BYTES) {
                            dir.mkdirs()
                            file = File.createTempFile(SPOOL_PREFIX, null, dir)
                            out = BufferedOutputStream(file.outputStream(), Limits.WIRE_CHUNK_BYTES).also { head.writeTo(it) }
                        }
                        out.write(buffer, 0, n)
                        total += n
                        onBytes(total)
                    }
                    out.close()
                } catch (e: Throwable) {
                    runCatching { out.close() }
                    file?.delete()
                    throw e
                }
                return Spool(if (file == null) head.toByteArray() else null, file, total)
            }
        }
    }

    private companion object {
        const val UTF_8 = "UTF-8"
        const val GZIP_LEVEL = 6
        const val XZ_PRESET = 6
        const val BZIP2_BLOCK = 9
        const val SPOOL_PREFIX = "tar"
    }
}
