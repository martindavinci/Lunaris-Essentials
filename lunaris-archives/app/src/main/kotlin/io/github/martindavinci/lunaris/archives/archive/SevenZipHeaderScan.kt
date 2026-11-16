package io.github.martindavinci.lunaris.archives.archive

/** A 7z header that cannot be followed: the caller answers "nothing visible". */
internal class SevenZipHeaderBroken : Exception()

internal class SevenZipCoder(val id: ByteArray, val properties: ByteArray?)
internal class SevenZipFolder(val coders: List<SevenZipCoder>)

/** The part of a 7z StreamsInfo the KDF check needs: where the packed streams are and how they were coded. */
internal class SevenZipStreams(val packPosition: Long, val packSizes: List<Long>, val folders: List<SevenZipFolder>, val unpackSizes: List<Long>)

/** Reads the 7z header structures (7zFormat.txt, "Property IDs" and "Folder"). Every count is bounded by the bytes left. */
internal class SevenZipHeaderScan(private val data: ByteArray) {
    private var at = 0

    fun byte(): Int {
        if (at >= data.size) throw SevenZipHeaderBroken()
        return data[at++].toInt() and BYTE_MASK
    }

    /** The 7z variable-length number: the leading one-bits of the first byte say how many bytes follow. */
    fun number(): Long {
        val first = byte()
        var mask = FIRST_BIT
        var value = 0L
        for (i in 0 until NUMBER_MAX_BYTES) {
            if (first and mask == 0) return value or ((first and (mask - 1)).toLong() shl (BITS_PER_BYTE * i))
            value = value or (byte().toLong() shl (BITS_PER_BYTE * i))
            mask = mask ushr 1
        }
        return value
    }

    /** A count that must fit in what is left of the header, so it can be used to size a loop. */
    private fun count(): Int {
        val n = number()
        if (n < 0 || n > data.size - at) throw SevenZipHeaderBroken()
        return n.toInt()
    }

    private fun bytes(n: Int): ByteArray {
        if (n < 0 || n > data.size - at) throw SevenZipHeaderBroken()
        return data.copyOfRange(at, at + n).also { at += n }
    }

    fun skipArchiveProperties() {
        while (byte() != ID_END) bytes(count())
    }

    /** Pack info and the folders; the cursor is left after the unpack sizes (sub-streams are not read). */
    fun streams(): SevenZipStreams {
        var id = byte()
        var packPosition = 0L
        var packSizes = emptyList<Long>()
        if (id == ID_PACK_INFO) {
            packPosition = number()
            val streams = count()
            id = byte()
            if (id == ID_SIZE) {
                packSizes = List(streams) { number() }
                id = byte()
            }
            if (id == ID_CRC) {
                skipDigests(streams)
                id = byte()
            }
            if (id != ID_END) throw SevenZipHeaderBroken()
            id = byte()
        }
        if (id != ID_UNPACK_INFO) return SevenZipStreams(packPosition, packSizes, emptyList(), emptyList())
        if (byte() != ID_FOLDER) throw SevenZipHeaderBroken()
        val folderCount = count()
        if (byte() != 0) throw SevenZipHeaderBroken()          // "external" folders: not used by any writer
        val outputs = ArrayList<Int>(folderCount)
        val folders = List(folderCount) { folder(outputs) }
        if (byte() != ID_CODERS_UNPACK_SIZE) throw SevenZipHeaderBroken()
        val unpackSizes = ArrayList<Long>()
        outputs.forEach { n -> repeat(n) { unpackSizes += number() } }
        return SevenZipStreams(packPosition, packSizes, folders, unpackSizes)
    }

    private fun folder(outputs: MutableList<Int>): SevenZipFolder {
        val coderCount = count()
        if (coderCount > MAX_CODERS) throw SevenZipHeaderBroken()
        var inputs = 0
        var outs = 0
        val coders = List(coderCount) {
            val flags = byte()
            if (flags and FLAG_RESERVED != 0) throw SevenZipHeaderBroken()
            val id = bytes(flags and FLAG_ID_SIZE)
            if (flags and FLAG_COMPLEX != 0) {
                inputs += count()
                outs += count()
            } else {
                inputs++
                outs++
            }
            SevenZipCoder(id, if (flags and FLAG_PROPERTIES != 0) bytes(count()) else null)
        }
        val bindPairs = (outs - 1).coerceAtLeast(0)
        repeat(bindPairs) { number(); number() }
        val packed = inputs - bindPairs
        if (packed > 1) repeat(packed) { number() }
        outputs += outs
        return SevenZipFolder(coders)
    }

    private fun skipDigests(streams: Int) {
        var defined = streams
        if (byte() == 0) {                                       // not all defined: a bit per stream follows
            val bits = bytes((streams + BITS_PER_BYTE - 1) / BITS_PER_BYTE)
            defined = bits.sumOf { Integer.bitCount(it.toInt() and BYTE_MASK) }
        }
        if (defined > (data.size - at) / CRC_BYTES) throw SevenZipHeaderBroken()
        bytes(defined * CRC_BYTES)
    }

    companion object {
        const val ID_END = 0x00
        const val ID_HEADER = 0x01
        const val ID_ARCHIVE_PROPERTIES = 0x02
        const val ID_ADDITIONAL_STREAMS = 0x03
        const val ID_MAIN_STREAMS = 0x04
        const val ID_ENCODED_HEADER = 0x17
        private const val ID_PACK_INFO = 0x06
        private const val ID_UNPACK_INFO = 0x07
        private const val ID_SIZE = 0x09
        private const val ID_CRC = 0x0A
        private const val ID_FOLDER = 0x0B
        private const val ID_CODERS_UNPACK_SIZE = 0x0C
        private const val FLAG_ID_SIZE = 0x0F
        private const val FLAG_COMPLEX = 0x10
        private const val FLAG_PROPERTIES = 0x20
        private const val FLAG_RESERVED = 0xC0
        private const val MAX_CODERS = 64
        private const val FIRST_BIT = 0x80
        private const val NUMBER_MAX_BYTES = 8
        private const val BITS_PER_BYTE = 8
        private const val BYTE_MASK = 0xFF
        private const val CRC_BYTES = 4
    }
}
