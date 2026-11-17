package io.github.martindavinci.lunaris.archives.create

import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.AesVersion
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.model.enums.CompressionLevel as Zip4jLevel

/** A read failure of a source file, kept apart from write failures of the ZIP. */
class SourceReadException(cause: IOException) : IOException(cause)

/** Streams files into a ZIP: Store / Normal / Maximum, optional AES-256 (names stay readable: a limit of the format). */
class ZipWriter(out: OutputStream, private val level: CompressionLevel, password: CharArray?) : ArchiveWriter {
    private val secret = password?.copyOf()
    private val zip = ZipOutputStream(BufferedOutputStream(out, Limits.WIRE_CHUNK_BYTES), secret)

    /** [onBytes] gets the bytes added so far for this file; it may throw to stop (cancellation). */
    override fun add(path: String, sizeBytes: Long?, modifiedMillis: Long?, input: InputStream, onBytes: (Long) -> Unit) {
        input.use { source ->
            zip.putNextEntry(parameters(path, sizeBytes, modifiedMillis))   // inside use: a refused header still closes the input
            val buffer = ByteArray(Limits.WIRE_CHUNK_BYTES)
            var total = 0L
            while (true) {
                val n = try {
                    source.read(buffer)
                } catch (e: IOException) {
                    throw SourceReadException(e)
                }
                if (n < 0) break
                zip.write(buffer, 0, n)
                total += n
                onBytes(total)
            }
        }
        zip.closeEntry()
    }

    override fun close() {
        try {
            zip.close()
        } finally {
            secret?.fill(WIPED)
        }
    }

    private fun parameters(path: String, sizeBytes: Long?, modifiedMillis: Long?) = ZipParameters().apply {
        fileNameInZip = path
        // zip4j needs the size up front to store without compression; when it is unknown, deflate.
        compressionMethod = if (level == CompressionLevel.STORE && sizeBytes != null) CompressionMethod.STORE else CompressionMethod.DEFLATE
        compressionLevel = if (level == CompressionLevel.MAXIMUM) Zip4jLevel.ULTRA else Zip4jLevel.NORMAL
        lastModifiedFileTime = modifiedMillis ?: System.currentTimeMillis()
        sizeBytes?.let { entrySize = it }
        if (secret != null) {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            aesVersion = AesVersion.TWO
        }
    }

    private companion object { val WIPED = Char(0) }
}
