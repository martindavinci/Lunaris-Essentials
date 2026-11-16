package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.RawPath
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.OutputStream

/** Writes EngineWire messages. Not thread-safe. */
class WireWriter(out: OutputStream) : Closeable {
    private val data = DataOutputStream(BufferedOutputStream(out, EngineWire.CHUNK_BYTES + HEADER_ROOM))

    init { data.writeByte(EngineWire.VERSION) }

    fun archive(a: WireArchive) = with(data) {
        writeByte(EngineWire.TAG_ARCHIVE); writeByte(a.kind.code); writeBoolean(a.innerTar)
        writeByte(a.encryption.code); bytesOrNull(a.comment); writeBoolean(a.capped); writeBoolean(a.partial)
    }

    fun entry(e: WireEntry) = with(data) {
        writeByte(EngineWire.TAG_ENTRY); writeLong(e.index); bytes(e.rawPath.bytes); writeBoolean(e.pathIsUtf8)
        writeByte(e.type.code); longOrNull(e.size); longOrNull(e.packedSize); longOrNull(e.mtimeMillis)
        writeBoolean(e.encrypted); longOrNull(e.crc); bytesOrNull(e.linkTarget?.bytes)
        writeBoolean(e.host != null); e.host?.let { writeByte(it.code) }
    }

    fun dataStart(index: Long, rawPath: RawPath) = with(data) {
        writeByte(EngineWire.TAG_DATA_START); writeLong(index); bytes(rawPath.bytes)
    }

    fun data(buffer: ByteArray, offset: Int, length: Int) = with(data) {
        writeByte(EngineWire.TAG_DATA); writeInt(length); write(buffer, offset, length)
    }

    fun progress(archiveBytesRead: Long) = with(data) { writeByte(EngineWire.TAG_PROGRESS); writeLong(archiveBytesRead) }

    fun dataEnd(index: Long, outcome: EntryOutcome) = with(data) {
        writeByte(EngineWire.TAG_DATA_END); writeLong(index); writeByte(outcome.code)
    }

    fun status(status: EngineStatus) = with(data) { writeByte(EngineWire.TAG_STATUS); writeByte(status.code); flush() }

    fun flush() = data.flush()

    override fun close() = data.close()

    private fun bytes(b: ByteArray) { data.writeInt(b.size); data.write(b) }
    private fun bytesOrNull(b: ByteArray?) { data.writeBoolean(b != null); b?.let(::bytes) }
    private fun longOrNull(v: Long?) { data.writeBoolean(v != null); v?.let(data::writeLong) }

    private companion object { const val HEADER_ROOM = 64 }
}
