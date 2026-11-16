package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.ArchiveSignatures
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryNames
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.Limits
import io.github.martindavinci.lunaris.archives.safety.SafePath
import io.github.martindavinci.lunaris.archives.safety.SkipReason

/**
 * Main-process side of the wire: turns what the engine says into an index or into events.
 * This is where the engine stops being trusted: entries are capped in number and name bytes,
 * must come in order, and data must belong to an entry.
 */
object WireCollector {
    data class Collected(val status: EngineStatus, val index: ArchiveIndex?)

    /** [onEntries] is told how many entries have arrived, for "Reading the archive… N items". */
    fun index(reader: WireReader, hint: ArchiveKind, onEntries: (Int) -> Unit = {}): Collected {
        val wire = ArrayList<WireEntry>()
        var archive: WireArchive? = null
        var lastIndex = -1L
        var pathBytes = 0L
        var dropped = false
        while (true) {
            when (val message = reader.next() ?: throw WireFormatException("ended without status")) {
                is WireMessage.Entry -> {
                    val entry = message.entry
                    if (entry.index <= lastIndex) throw WireFormatException("entries out of order")
                    lastIndex = entry.index
                    val kept = retained(entry)
                    val size = kept.rawPath.size
                    if (wire.size >= Limits.MAX_ENTRIES || pathBytes + size > Limits.MAX_INDEX_PATH_BYTES) {
                        dropped = true
                    } else {
                        pathBytes += size
                        wire += kept
                        if (wire.size % Limits.INDEX_PROGRESS_EVERY_ENTRIES == 0) onEntries(wire.size)
                    }
                }
                is WireMessage.Archive -> archive = message.archive
                is WireMessage.Status -> return Collected(message.status, archive?.let { build(it, wire, hint, dropped) })
                else -> throw WireFormatException("data in a listing")
            }
        }
    }

    /**
     * What is kept of an entry until the index is built: the head of its name (one byte more than a
     * name may have, so "too long" is still seen) and no link target, which nothing reads. A hostile
     * engine sending 200 000 names of 64 KiB then costs megabytes, not gigabytes.
     */
    internal fun retained(entry: WireEntry): WireEntry =
        entry.copy(rawPath = entry.rawPath.head(Limits.MAX_PATH_BYTES + 1), linkTarget = null)

    private fun build(archive: WireArchive, wire: List<WireEntry>, hint: ArchiveKind, dropped: Boolean): ArchiveIndex {
        val kind = if (archive.kind == ArchiveKind.UNKNOWN) hint else archive.kind
        val pool = HashMap<String, String>()
        val entries = wire.map { toEntry(it, zip = kind == ArchiveKind.ZIP, pool) }
        val files = entries.filter { it.type == EntryType.FILE }
        val encryption = when {
            archive.encryption == Encryption.HEADERS -> Encryption.HEADERS
            files.none { it.encrypted } -> Encryption.NONE
            files.all { it.encrypted } -> Encryption.ALL_ENTRIES
            else -> Encryption.SOME_ENTRIES
        }
        val comment = archive.comment?.let { EntryNames.display(EntryNames.decode(it, utf8Flag = false, zipWithoutEfs = kind == ArchiveKind.ZIP)) }
        val host: HostOs? = wire.firstNotNullOfOrNull { it.host }
        return ArchiveIndex(
            kind, ArchiveSignatures.formatLabel(kind, archive.innerTar), entries, encryption, comment, host,
            cappedAtLimit = archive.capped || dropped, partial = archive.partial,
        )
    }

    private fun toEntry(wire: WireEntry, zip: Boolean, pool: HashMap<String, String>): ArchiveEntry {
        // A name over the limit is skipped anyway: keep its head only, so 200 000 of them stay small.
        val tooLong = wire.rawPath.size > Limits.MAX_PATH_BYTES
        val raw = wire.rawPath.head(Limits.MAX_PATH_BYTES)
        val name = EntryNames.decode(raw.bytes, wire.pathIsUtf8, zipWithoutEfs = zip && !wire.pathIsUtf8)
        val path = if (tooLong) SafePath.Result.Skipped(SkipReason.INVALID_NAME) else pooled(SafePath.evaluate(name, wire.type), pool)
        return ArchiveEntry(
            wire.index, raw, EntryNames.display(name), path, wire.type,
            wire.size?.takeIf { it >= 0 }, wire.packedSize?.takeIf { it >= 0 }, wire.mtimeMillis,
            wire.encrypted, wire.crc, wire.pathIsUtf8,
        )
    }

    /** The same folder names repeat in every entry below them: one String each, not one per entry. */
    private fun pooled(path: SafePath.Result, pool: HashMap<String, String>): SafePath.Result =
        if (path is SafePath.Result.Ok) SafePath.Result.Ok(path.components.map { pool.getOrPut(it) { it } }) else path

    /**
     * Events of an extraction. Only the entries of [expected] may arrive, each once, with the
     * raw path the index has for them; bytes may only come between a start and its end.
     */
    fun events(reader: WireReader, expected: Map<Long, RawPath>): Sequence<EngineEvent> = sequence {
        val started = HashSet<Long>()
        var open: Long? = null
        while (true) {
            when (val message = reader.next() ?: throw WireFormatException("ended without status")) {
                is WireMessage.DataStart -> {
                    if (open != null || !started.add(message.index)) throw WireFormatException("entry started twice")
                    if (!message.rawPath.sameEntryAs(expected[message.index])) {
                        yield(EngineEvent.Finished(EngineStatus.PATH_MISMATCH))
                        return@sequence
                    }
                    open = message.index
                    yield(EngineEvent.EntryStarted(message.index))
                }
                is WireMessage.Data -> {
                    if (open == null) throw WireFormatException("data outside an entry")
                    yield(EngineEvent.Bytes(message.bytes, message.length))
                }
                is WireMessage.Progress -> yield(EngineEvent.Progress(message.archiveBytesRead))
                is WireMessage.DataEnd -> {
                    if (open != message.index) throw WireFormatException("end of another entry")
                    open = null
                    yield(EngineEvent.EntryFinished(message.index, message.outcome))
                }
                is WireMessage.Status -> {
                    // A reader that failed in the middle of an entry: close it for the consumer.
                    open?.let { yield(EngineEvent.EntryFinished(it, EntryOutcome.DAMAGED)) }
                    yield(EngineEvent.Finished(message.status))
                    return@sequence
                }
                else -> throw WireFormatException("listing in an extraction")
            }
        }
    }
}
