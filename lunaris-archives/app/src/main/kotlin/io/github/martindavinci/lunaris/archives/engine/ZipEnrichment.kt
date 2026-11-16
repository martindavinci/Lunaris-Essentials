package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.archive.ZipCdEntry
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import java.io.IOException

/**
 * Adds to libarchive's ZIP listing what only the central directory knows: packed size, CRC,
 * host system, the archive comment.
 *
 * libarchive lists entries by local header offset and names them from the local header, so the
 * i-th listed entry is paired with the i-th entry of [ZipDirectory.inReadOrder], even when the
 * directory is in another order or holds two entries of one name. Names are only the fallback
 * when the two lists differ in length.
 */
object ZipEnrichment {
    class Result(val entries: List<WireEntry>, val comment: ByteArray?)

    fun apply(kind: ArchiveKind, volume: EngineVolume, entries: List<WireEntry>): Result {
        if (kind != ArchiveKind.ZIP) return Result(entries, null)
        val directory = try {
            ZipDirectory.read(volume.channel)
        } catch (_: IOException) {
            return Result(entries, null)
        }
        val ordered = directory.inReadOrder
        val merged = if (ordered.size == entries.size) {
            entries.mapIndexed { i, entry -> merge(entry, ordered[i]) }
        } else {
            byName(entries, ordered)
        }
        return Result(merged, directory.comment)
    }

    /** For a listing that stopped early or a directory libarchive read differently: same name, in order. */
    private fun byName(entries: List<WireEntry>, ordered: List<ZipCdEntry>): List<WireEntry> {
        val waiting = HashMap<RawPath, ArrayDeque<ZipCdEntry>>()
        ordered.forEach { waiting.getOrPut(it.name) { ArrayDeque() }.addLast(it) }
        return entries.map { entry -> waiting[entry.rawPath]?.removeFirstOrNull()?.let { merge(entry, it) } ?: entry }
    }

    /**
     * The raw path stays libarchive's (the local header's): it is what DATA_START will carry,
     * from libarchive and from ZipCryptoReader alike, and what the main process compares.
     */
    private fun merge(entry: WireEntry, cd: ZipCdEntry): WireEntry = entry.copy(
        pathIsUtf8 = cd.efs || entry.pathIsUtf8,
        packedSize = cd.packedSize,
        crc = cd.crc,
        host = cd.madeBy,
        encrypted = entry.encrypted || cd.encrypted,
    )
}
