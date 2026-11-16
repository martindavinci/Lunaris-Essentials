package io.github.martindavinci.lunaris.archives.archive

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val MASK_8 = 0xFF
private const val MASK_16 = 0xFFFF
private const val MASK_32 = 0xFFFFFFFFL
private const val LONG_BYTES = 8

private fun u16(buffer: ByteBuffer, at: Int): Int = buffer.getShort(at).toInt() and MASK_16
private fun u32(buffer: ByteBuffer, at: Int): Long = buffer.getInt(at).toLong() and MASK_32

/** A sliding window over the central directory: a huge (or lying) directory is never read whole. */
internal class CdWindow(private val channel: FileChannel, private val start: Long, private val end: Long) {
    private var buffer: ByteBuffer = ByteBuffer.allocate(0)
    private var bufferStart = start

    fun has(position: Long, length: Int): Boolean = position >= start && length >= 0 && position <= end - length

    /** [length] bytes at [position], little-endian, positioned at 0. Check [has] first. */
    fun at(position: Long, length: Int): ByteBuffer {
        if (position < bufferStart || position + length > bufferStart + buffer.limit()) {
            val load = minOf(end - position, maxOf(length, WINDOW_BYTES).toLong()).toInt()
            buffer = ZipDirectory.readAt(channel, position, load)
            bufferStart = position
        }
        val view = buffer.duplicate()
        view.position((position - bufferStart).toInt())
        view.limit(view.position() + length)
        return view.slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    private companion object { const val WINDOW_BYTES = 1024 * 1024 }
}

/** One central directory record. Offsets are those of the ZIP specification (APPNOTE 4.3.12). */
internal object CdParser {
    private const val SIGNATURE = 0x02014b50
    private const val FIXED_BYTES = 46
    private const val MADE_BY = 4
    private const val FLAGS = 8
    private const val METHOD = 10
    private const val DOS_TIME = 12
    private const val DOS_DATE = 14
    private const val CRC = 16
    private const val PACKED_SIZE = 20
    private const val SIZE = 24
    private const val NAME_LENGTH = 28
    private const val EXTRA_LENGTH = 30
    private const val COMMENT_LENGTH = 32
    private const val LOCAL_OFFSET = 42
    private const val FLAG_ENCRYPTED = 0x0001
    private const val FLAG_EFS = 0x0800
    private const val EXTRA_HEADER_BYTES = 4
    private const val ZIP64_ID = 0x0001
    private const val AES_ID = 0x9901
    private const val AES_BYTES = 7
    private const val AES_STRENGTH = 4
    private const val AES_METHOD = 5
    private const val AES_BASE_BITS = 64
    private const val AES_STEP_BITS = 64
    private const val HOST_SHIFT = 8
    private const val DATE_SHIFT = 16

    /** Length of the record at [position], or null when there is no whole record there. */
    fun recordLength(window: CdWindow, position: Long): Int? {
        if (!window.has(position, FIXED_BYTES)) return null
        val head = window.at(position, FIXED_BYTES)
        if (head.getInt(0) != SIGNATURE) return null
        val length = FIXED_BYTES + u16(head, NAME_LENGTH) + u16(head, EXTRA_LENGTH) + u16(head, COMMENT_LENGTH)
        return length.takeIf { window.has(position, it) }
    }

    fun parse(record: ByteBuffer, order: Int): ZipCdEntry {
        val flags = u16(record, FLAGS)
        val name = ByteArray(u16(record, NAME_LENGTH))
        record.position(FIXED_BYTES)
        record.get(name)
        val extra = record.slice().order(ByteOrder.LITTLE_ENDIAN)
        extra.limit(u16(record, EXTRA_LENGTH))
        val sizes = Sizes(u32(record, SIZE), u32(record, PACKED_SIZE), u32(record, LOCAL_OFFSET))
        val aes = readExtras(extra, sizes)
        return ZipCdEntry(
            order = order, name = RawPath.of(name), efs = flags and FLAG_EFS != 0, method = u16(record, METHOD),
            encrypted = flags and FLAG_ENCRYPTED != 0, aes = aes, crc = u32(record, CRC),
            packedSize = sizes.packed.coerceAtLeast(0), size = sizes.size.coerceAtLeast(0),
            localHeaderOffset = sizes.offset.coerceAtLeast(0), madeBy = host(u16(record, MADE_BY) ushr HOST_SHIFT),
            dosTime = (u16(record, DOS_DATE).toLong() shl DATE_SHIFT) or u16(record, DOS_TIME).toLong(),
        )
    }

    private class Sizes(var size: Long, var packed: Long, var offset: Long)

    /** Zip64 replaces, in this order, the 32-bit fields that are all ones; 0x9901 says how AES was used. */
    private fun readExtras(extra: ByteBuffer, sizes: Sizes): AesInfo? {
        var aes: AesInfo? = null
        while (extra.remaining() >= EXTRA_HEADER_BYTES) {
            val id = extra.getShort().toInt() and MASK_16
            val length = extra.getShort().toInt() and MASK_16
            if (length > extra.remaining()) break
            val field = extra.slice().order(ByteOrder.LITTLE_ENDIAN)
            field.limit(length)
            extra.position(extra.position() + length)
            if (id == ZIP64_ID) {
                if (sizes.size == MASK_32 && field.remaining() >= LONG_BYTES) sizes.size = field.getLong()
                if (sizes.packed == MASK_32 && field.remaining() >= LONG_BYTES) sizes.packed = field.getLong()
                if (sizes.offset == MASK_32 && field.remaining() >= LONG_BYTES) sizes.offset = field.getLong()
            } else if (id == AES_ID && length >= AES_BYTES) {
                val strength = field.get(AES_STRENGTH).toInt() and MASK_8
                aes = AesInfo(AES_BASE_BITS + AES_STEP_BITS * strength, u16(field, AES_METHOD))
            }
        }
        return aes
    }

    private fun host(code: Int): HostOs = when (code) {
        0 -> HostOs.MSDOS
        3 -> HostOs.UNIX
        7, 19 -> HostOs.MACOS
        10, 11, 14 -> HostOs.WINDOWS
        else -> HostOs.OTHER
    }
}

/** The local header of one entry: its name is the one libarchive lists (not the directory's). */
internal object LocalHeader {
    private const val SIGNATURE = 0x04034b50
    private const val FIXED_BYTES = 30
    private const val NAME_LENGTH = 26

    /** Null when there is no local header at [offset]. */
    fun name(channel: FileChannel, offset: Long): RawPath? {
        val size = channel.size()
        if (offset < 0 || offset > size - FIXED_BYTES) return null
        val head = ZipDirectory.readAt(channel, offset, FIXED_BYTES)
        if (head.getInt(0) != SIGNATURE) return null
        val length = u16(head, NAME_LENGTH)
        if (offset + FIXED_BYTES + length > size) return null
        val name = ByteArray(length)
        ZipDirectory.readAt(channel, offset + FIXED_BYTES, length).get(name)
        return RawPath.owning(name)
    }
}
