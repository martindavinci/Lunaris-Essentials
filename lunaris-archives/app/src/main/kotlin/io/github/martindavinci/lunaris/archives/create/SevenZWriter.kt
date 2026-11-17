package io.github.martindavinci.lunaris.archives.create

import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.tukaani.xz.LZMA2Options

/**
 * A 7z, without a password (Commons Compress cannot write one). The format needs to seek back, so it is written
 * to a work file in [workDir] and copied into [out] at the end, reported through [onFinishing]. An archive that
 * failed or was aborted is never copied: its work file is only deleted.
 */
class SevenZWriter(
    private val out: OutputStream,
    private val level: CompressionLevel,
    workDir: File,
    private val onFinishing: (copied: Long, total: Long) -> Unit = { _, _ -> },
) : ArchiveWriter {
    private val work = workDir.apply { mkdirs() }.let { File.createTempFile(WORK_PREFIX, null, it) }
    private val seven = SevenZOutputFile(work)
    private var aborted = false

    override fun add(path: String, sizeBytes: Long?, modifiedMillis: Long?, input: InputStream, onBytes: (Long) -> Unit) {
        try {
            input.use { source ->
                val entry = SevenZArchiveEntry().apply {
                    name = path
                    lastModifiedDate = Date(modifiedMillis ?: System.currentTimeMillis())
                    setContentMethods(listOf(method(sizeBytes)))
                }
                seven.putArchiveEntry(entry)
                val buffer = ByteArray(Limits.WIRE_CHUNK_BYTES)
                var total = 0L
                while (true) {
                    val n = try {
                        source.read(buffer)
                    } catch (e: IOException) {
                        throw SourceReadException(e)
                    }
                    if (n < 0) break
                    seven.write(buffer, 0, n)
                    total += n
                    onBytes(total)
                }
                seven.closeArchiveEntry()
            }
        } catch (e: Throwable) {
            aborted = true
            throw e
        }
    }

    override fun abort() {
        aborted = true
    }

    override fun close() {
        try {
            if (aborted) {
                runCatching { seven.close() }
                return
            }
            seven.close()
            val total = work.length()
            var copied = 0L
            work.inputStream().use { input ->
                out.use { target ->
                    val buffer = ByteArray(Limits.WIRE_CHUNK_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        target.write(buffer, 0, n)
                        copied += n
                        onFinishing(copied, total)
                    }
                }
            }
        } finally {
            work.delete()
        }
    }

    /** LZMA2 with a dictionary no bigger than the file: thousands of small files must not each take ~94 MiB. */
    private fun method(sizeBytes: Long?): SevenZMethodConfiguration {
        val preset = when (level) {
            CompressionLevel.STORE -> return SevenZMethodConfiguration(SevenZMethod.COPY)
            CompressionLevel.NORMAL -> Limits.SEVEN_Z_NORMAL_PRESET
            CompressionLevel.MAXIMUM -> Limits.SEVEN_Z_MAXIMUM_PRESET
        }
        val options = LZMA2Options(preset)
        if (sizeBytes != null) {
            val fitting = java.lang.Long.highestOneBit(maxOf(sizeBytes, 1L)).let { if (it < sizeBytes) it shl 1 else it }
            options.dictSize = fitting.coerceIn(LZMA2Options.DICT_SIZE_MIN.toLong(), options.dictSize.toLong()).toInt()
        }
        return SevenZMethodConfiguration(SevenZMethod.LZMA2, options)
    }

    private companion object { const val WORK_PREFIX = "new7z" }
}
