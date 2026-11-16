package io.github.martindavinci.lunaris.archives.archive

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.nio.channels.FileChannel
import org.tukaani.xz.LZMAInputStream

/**
 * Looks for the cost of the 7z key derivation before Commons Compress runs it: its loop of
 * 2^numCyclesPower SHA-256 rounds has no limit and cannot be interrupted.
 *
 * With the usual "encrypt files, not names" the AES coders of the files sit inside an
 * LZMA-compressed header, so such a header is unpacked first. With encrypted headers (7z -mhe)
 * the coders of the files are only readable after the password; there the guard that always holds
 * is the engine's own: a job that ignores a cancel for two seconds ends the engine process.
 */
object SevenZipKdfCheck {
    const val MAX_CYCLES_POWER = Limits.KDF_MAX_LOG2

    private const val START_BYTES = 32
    private const val NEXT_HEADER_OFFSET = 12
    private const val NEXT_HEADER_SIZE = 20
    private const val MAX_HEADER_BYTES = 16 * 1024 * 1024
    private const val MAX_DICTIONARY_BYTES = 32 * 1024 * 1024
    private const val CYCLES_MASK = 0x3F
    private const val LZMA_PROPERTIES_BYTES = 5
    private val SIGNATURE = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    private val AES_ID = byteArrayOf(0x06, 0xF1.toByte(), 0x07, 0x01)
    private val LZMA_ID = byteArrayOf(0x03, 0x01, 0x01)

    private class Seen(val cyclesPower: Int?, val headersEncrypted: Boolean)

    /** The highest numCyclesPower among the AES coders that can be seen without the password; null if none. */
    fun cyclesPower(channel: FileChannel): Int? = inspect(channel)?.cyclesPower

    /** True when the list of entries itself is behind AES (7z -mhe). */
    fun headersEncrypted(channel: FileChannel): Boolean = inspect(channel)?.headersEncrypted == true

    private fun inspect(channel: FileChannel): Seen? {
        try {
            val scan = SevenZipHeaderScan(nextHeader(channel) ?: return null)
            return when (scan.byte()) {
                SevenZipHeaderScan.ID_ENCODED_HEADER -> encodedHeader(channel, scan.streams())
                SevenZipHeaderScan.ID_HEADER -> Seen(plainHeader(scan), headersEncrypted = false)
                else -> null
            }
        } catch (_: SevenZipHeaderBroken) {
            return null   // Commons Compress will say what is wrong with the archive
        } catch (_: IOException) {
            return null
        }
    }

    private fun nextHeader(channel: FileChannel): ByteArray? {
        if (channel.size() < START_BYTES) return null
        val start = read(channel, 0, START_BYTES)
        if (!start.copyOf(SIGNATURE.size).contentEquals(SIGNATURE)) return null
        val fields = ByteBuffer.wrap(start).order(ByteOrder.LITTLE_ENDIAN)
        val offset = fields.getLong(NEXT_HEADER_OFFSET)
        val size = fields.getLong(NEXT_HEADER_SIZE)
        if (size < 1 || size > MAX_HEADER_BYTES || offset < 0 || offset > channel.size() - START_BYTES - size) return null
        return read(channel, START_BYTES + offset, size.toInt())
    }

    private fun encodedHeader(channel: FileChannel, streams: SevenZipStreams): Seen? {
        val folder = streams.folders.firstOrNull() ?: return null
        aesPower(folder)?.let { return Seen(it, headersEncrypted = true) }
        val scan = SevenZipHeaderScan(unpackLzma(channel, streams, folder) ?: return null)
        if (scan.byte() != SevenZipHeaderScan.ID_HEADER) return null
        return Seen(plainHeader(scan), headersEncrypted = false)
    }

    /** After the header id: optional archive properties, then the streams of the files. */
    private fun plainHeader(scan: SevenZipHeaderScan): Int? {
        var id = scan.byte()
        if (id == SevenZipHeaderScan.ID_ARCHIVE_PROPERTIES) {
            scan.skipArchiveProperties()
            id = scan.byte()
        }
        if (id != SevenZipHeaderScan.ID_MAIN_STREAMS) return null
        return scan.streams().folders.mapNotNull(::aesPower).maxOrNull()
    }

    private fun aesPower(folder: SevenZipFolder): Int? = folder.coders
        .filter { it.id.contentEquals(AES_ID) }
        .mapNotNull { it.properties?.firstOrNull() }
        .maxOfOrNull { it.toInt() and CYCLES_MASK }

    /** The header as 7-Zip writes it by default: one LZMA stream. Anything else is left to Commons Compress. */
    private fun unpackLzma(channel: FileChannel, streams: SevenZipStreams, folder: SevenZipFolder): ByteArray? {
        val coder = folder.coders.singleOrNull()?.takeIf { it.id.contentEquals(LZMA_ID) } ?: return null
        val properties = coder.properties?.takeIf { it.size == LZMA_PROPERTIES_BYTES } ?: return null
        val packed = streams.packSizes.firstOrNull() ?: return null
        val unpacked = streams.unpackSizes.firstOrNull() ?: return null
        val dictionary = ByteBuffer.wrap(properties, 1, Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt()
        val position = START_BYTES + streams.packPosition
        val fits = packed in 1..MAX_HEADER_BYTES && unpacked in 1..MAX_HEADER_BYTES &&
            dictionary in 0..MAX_DICTIONARY_BYTES && streams.packPosition >= 0 && position <= channel.size() - packed
        if (!fits) return null
        val source = ByteArrayInputStream(read(channel, position, packed.toInt()))
        val header = ByteArray(unpacked.toInt())
        LZMAInputStream(source, unpacked, properties[0], dictionary).use { lzma ->
            var done = 0
            while (done < header.size) {
                val n = lzma.read(header, done, header.size - done)
                if (n < 0) return null
                done += n
            }
        }
        return header
    }

    private fun read(channel: FileChannel, position: Long, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        var at = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, at)
            if (n < 0) throw IOException("file ends early")
            at += n
        }
        return buffer.array()
    }
}
