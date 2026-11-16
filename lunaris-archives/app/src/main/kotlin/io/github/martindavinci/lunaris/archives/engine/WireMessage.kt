package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.archive.RawPath

/** One index record as the engine sends it: raw names, nothing decided yet. */
data class WireEntry(
    val index: Long, val rawPath: RawPath, val pathIsUtf8: Boolean, val type: EntryType, val size: Long?,
    val packedSize: Long?, val mtimeMillis: Long?, val encrypted: Boolean, val crc: Long?, val linkTarget: RawPath?,
    val host: HostOs?,
)

/** What the engine knows about the whole archive. */
data class WireArchive(
    val kind: ArchiveKind, val innerTar: Boolean, val encryption: Encryption, val comment: ByteArray?,
    val capped: Boolean, val partial: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is WireArchive && kind == other.kind && innerTar == other.innerTar &&
        encryption == other.encryption && comment.contentEquals(other.comment) && capped == other.capped && partial == other.partial

    override fun hashCode(): Int =
        listOf(kind, innerTar, encryption, comment.contentHashCode(), capped, partial).hashCode()
}

sealed interface WireMessage {
    data class Entry(val entry: WireEntry) : WireMessage
    data class Archive(val archive: WireArchive) : WireMessage
    data class DataStart(val index: Long, val rawPath: RawPath) : WireMessage
    class Data(val bytes: ByteArray, val length: Int) : WireMessage
    data class Progress(val archiveBytesRead: Long) : WireMessage
    data class DataEnd(val index: Long, val outcome: EntryOutcome) : WireMessage
    data class Status(val status: EngineStatus) : WireMessage
}

class WireFormatException(message: String) : java.io.IOException(message)
