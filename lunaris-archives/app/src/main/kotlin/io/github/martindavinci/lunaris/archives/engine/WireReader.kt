package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.archive.RawPath
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Reads EngineWire messages. The stream comes from the isolated engine, which parses hostile
 * data: every length is bounded before anything is allocated, every code is checked, and nothing
 * after STATUS is read.
 */
class WireReader(input: InputStream) {
    private val data = DataInputStream(BufferedInputStream(input, EngineWire.CHUNK_BYTES + HEADER_ROOM))
    private var versionChecked = false
    private var finished = false

    /** The next message; null only after STATUS. Throws [WireFormatException] on anything else that ends the stream. */
    fun next(): WireMessage? {
        if (finished) return null
        try {
            if (!versionChecked) {
                if (data.readUnsignedByte() != EngineWire.VERSION) throw WireFormatException("version")
                versionChecked = true
            }
            return when (val tag = data.readUnsignedByte()) {
                EngineWire.TAG_ENTRY -> WireMessage.Entry(readEntry())
                EngineWire.TAG_ARCHIVE -> WireMessage.Archive(readArchive())
                EngineWire.TAG_DATA_START -> WireMessage.DataStart(data.readLong(), path())
                EngineWire.TAG_DATA -> readData()
                EngineWire.TAG_PROGRESS -> WireMessage.Progress(data.readLong())
                EngineWire.TAG_DATA_END -> WireMessage.DataEnd(data.readLong(), EntryOutcome.of(data.readUnsignedByte()))
                EngineWire.TAG_STATUS -> WireMessage.Status(EngineStatus.of(data.readUnsignedByte())).also { finished = true }
                else -> throw WireFormatException("tag $tag")
            }
        } catch (_: EOFException) {
            throw WireFormatException(if (versionChecked) "ended without status" else "empty")
        } catch (_: NoSuchElementException) {
            throw WireFormatException("unknown code")
        }
    }

    private fun readEntry(): WireEntry = WireEntry(
        index = data.readLong(),
        rawPath = path(),
        pathIsUtf8 = data.readBoolean(),
        type = EntryType.of(data.readUnsignedByte()),
        size = longOrNull(),
        packedSize = longOrNull(),
        mtimeMillis = longOrNull(),
        encrypted = data.readBoolean(),
        crc = longOrNull(),
        linkTarget = if (data.readBoolean()) path() else null,
        host = if (data.readBoolean()) HostOs.of(data.readUnsignedByte()) else null,
    )

    private fun readArchive(): WireArchive {
        val code = data.readUnsignedByte()
        val kind = ArchiveKind.entries.firstOrNull { it.code == code } ?: throw WireFormatException("unknown kind")
        return WireArchive(
            kind = kind,
            innerTar = data.readBoolean(),
            encryption = Encryption.of(data.readUnsignedByte()),
            comment = if (data.readBoolean()) bytes(EngineWire.MAX_COMMENT_BYTES) else null,
            capped = data.readBoolean(),
            partial = data.readBoolean(),
        )
    }

    private fun readData(): WireMessage.Data {
        val length = data.readInt()
        if (length < 1 || length > EngineWire.CHUNK_BYTES) throw WireFormatException("chunk of $length bytes")
        val chunk = ByteArray(length)
        data.readFully(chunk)
        return WireMessage.Data(chunk, length)
    }

    private fun path(): RawPath = RawPath.owning(bytes(EngineWire.MAX_PATH_BYTES))

    private fun bytes(max: Int): ByteArray {
        val length = data.readInt()
        if (length < 0 || length > max) throw WireFormatException("field of $length bytes")
        return ByteArray(length).also { data.readFully(it) }
    }

    private fun longOrNull(): Long? = if (data.readBoolean()) data.readLong() else null

    private companion object { const val HEADER_ROOM = 64 }
}
