package io.github.martindavinci.lunaris.archives.archive

import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class ZipCdEntry(
    val order: Int, val name: RawPath, val efs: Boolean, val method: Int, val encrypted: Boolean, val aes: AesInfo?,
    val crc: Long, val packedSize: Long, val size: Long, val localHeaderOffset: Long, val madeBy: HostOs, val dosTime: Long,
)

data class AesInfo(val strengthBits: Int, val actualMethod: Int)

class ZipDirectoryException(message: String) : IOException(message)

/** The central directory of a ZIP, read with positional reads only; nothing in it is trusted. */
class ZipDirectory private constructor(val entries: List<ZipCdEntry>, val comment: ByteArray?) {
    /**
     * The entries as libarchive lists them: by local header offset, one entry per offset (its
     * seekable ZIP reader keeps them in a tree keyed by that offset; a second entry with the
     * same offset is dropped). The index of an entry in a libarchive listing is its place in
     * THIS list, not in [entries]: anything that maps an index back to a ZIP entry must use it.
     */
    val inReadOrder: List<ZipCdEntry> by lazy {
        entries.distinctBy { it.localHeaderOffset }.sortedBy { it.localHeaderOffset }
    }

    companion object {
        private const val EOCD_SIGNATURE = 0x06054b50
        private const val EOCD_BYTES = 22
        private const val MAX_COMMENT_BYTES = 0xFFFF
        private const val EOCD_ENTRIES = 10
        private const val EOCD_CD_SIZE = 12
        private const val EOCD_CD_OFFSET = 16
        private const val EOCD_COMMENT_LENGTH = 20
        private const val LOCATOR_SIGNATURE = 0x07064b50
        private const val LOCATOR_BYTES = 20
        private const val LOCATOR_EOCD64_OFFSET = 8
        private const val EOCD64_SIGNATURE = 0x06064b50
        private const val EOCD64_BYTES = 56
        private const val EOCD64_CD_SIZE = 40
        private const val EOCD64_CD_OFFSET = 48
        private const val MASK_16 = 0xFFFF
        private const val MASK_32 = 0xFFFFFFFFL

        /** Throws [ZipDirectoryException] when there is no directory to read. */
        fun read(channel: FileChannel): ZipDirectory {
            val size = channel.size()
            if (size < EOCD_BYTES) throw ZipDirectoryException("no end record")
            val tailBytes = minOf(size, (EOCD_BYTES + MAX_COMMENT_BYTES).toLong()).toInt()
            val tailStart = size - tailBytes
            val tail = readAt(channel, tailStart, tailBytes)
            val at = findEnd(tail) ?: throw ZipDirectoryException("no end record")
            val end = tailStart + at
            var cdSize = tail.getInt(at + EOCD_CD_SIZE).toLong() and MASK_32
            var cdOffset = tail.getInt(at + EOCD_CD_OFFSET).toLong() and MASK_32
            val entryCount = tail.getShort(at + EOCD_ENTRIES).toInt() and MASK_16
            if (entryCount == MASK_16 || cdSize == MASK_32 || cdOffset == MASK_32) {
                zip64(channel, end)?.let { (size64, offset64) -> cdSize = size64; cdOffset = offset64 }
            }
            if (cdSize < 0 || cdOffset < 0 || cdOffset > end || cdSize > end - cdOffset) throw ZipDirectoryException("directory out of range")
            val commentLength = tail.getShort(at + EOCD_COMMENT_LENGTH).toInt() and MASK_16
            val comment = ByteArray(commentLength).takeIf { commentLength > 0 }?.also {
                tail.position(at + EOCD_BYTES)
                tail.get(it)
            }
            return ZipDirectory(readEntries(channel, cdOffset, cdSize), comment)
        }

        private fun readEntries(channel: FileChannel, cdOffset: Long, cdSize: Long): List<ZipCdEntry> {
            val window = CdWindow(channel, cdOffset, cdOffset + cdSize)
            val entries = ArrayList<ZipCdEntry>()
            var position = cdOffset
            // The count in the end record is not trusted: read records while they are there.
            while (entries.size < Limits.MAX_ENTRIES) {
                val length = CdParser.recordLength(window, position) ?: break
                entries += CdParser.parse(window.at(position, length), entries.size)
                position += length
            }
            return entries
        }

        /** Offset of the end record in [tail], searched from the back; its comment must fit behind it. */
        private fun findEnd(tail: ByteBuffer): Int? {
            var at = tail.limit() - EOCD_BYTES
            while (at >= 0) {
                if (tail.getInt(at) == EOCD_SIGNATURE) {
                    val commentLength = tail.getShort(at + EOCD_COMMENT_LENGTH).toInt() and MASK_16
                    if (at + EOCD_BYTES + commentLength <= tail.limit()) return at
                }
                at--
            }
            return null
        }

        /** Size and offset of the directory from the Zip64 records, when they are there and sane. */
        private fun zip64(channel: FileChannel, end: Long): Pair<Long, Long>? {
            if (end < LOCATOR_BYTES) return null
            val locator = readAt(channel, end - LOCATOR_BYTES, LOCATOR_BYTES)
            if (locator.getInt(0) != LOCATOR_SIGNATURE) return null
            val recordAt = locator.getLong(LOCATOR_EOCD64_OFFSET)
            if (recordAt < 0 || recordAt > end - EOCD64_BYTES) throw ZipDirectoryException("zip64 record out of range")
            val record = readAt(channel, recordAt, EOCD64_BYTES)
            if (record.getInt(0) != EOCD64_SIGNATURE) throw ZipDirectoryException("zip64 record missing")
            return record.getLong(EOCD64_CD_SIZE) to record.getLong(EOCD64_CD_OFFSET)
        }

        internal fun readAt(channel: FileChannel, position: Long, length: Int): ByteBuffer {
            val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
            var at = position
            while (buffer.hasRemaining()) {
                val n = channel.read(buffer, at)
                if (n < 0) throw ZipDirectoryException("file ends early")
                at += n
            }
            buffer.flip()
            return buffer
        }
    }
}
