package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.LocalHeader
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.archive.ZipCdEntry
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.FileHeader

/**
 * Encrypted ZIP entries through zip4j, one entry at a time, placed on its local header.
 * - an index is a place in [ZipDirectory.inReadOrder], the order libarchive listed, not in the
 *   central directory;
 * - DATA_START carries the local header's name, the one libarchive showed;
 * - zip4j closes the stream it reads from; [ChannelInput] ignores that, so the second entry of
 *   an extraction still has a channel to read (checked in the zip4j 2.11.6 sources:
 *   ZipInputStream.close → … → ZipEntryInputStream.close → inputStream.close).
 */
class ZipCryptoReader : IsolatedReader {
    override fun list(job: EngineJob, volume: EngineVolume, password: CharArray?, out: WireWriter): EngineStatus {
        val directory = try {
            ZipDirectory.read(volume.channel)
        } catch (_: IOException) {
            return EngineStatus.DAMAGED
        }
        val ordered = directory.inReadOrder
        ordered.forEachIndexed { i, cd ->
            job.throwIfCancelled()
            val name = LocalHeader.name(volume.channel, cd.localHeaderOffset) ?: cd.name
            out.send { entry(wireEntry(i.toLong(), name, cd)) }
        }
        val capped = directory.entries.size >= Limits.MAX_ENTRIES
        val encryption = if (ordered.any { it.encrypted }) Encryption.SOME_ENTRIES else Encryption.NONE
        out.send { archive(WireArchive(ArchiveKind.ZIP, false, encryption, directory.comment, capped, partial = false)) }
        return if (capped) EngineStatus.LIMIT_REACHED else EngineStatus.OK
    }

    override fun extract(
        job: EngineJob, volume: EngineVolume, password: CharArray?, indices: LongArray,
        expected: Map<Long, RawPath>?, out: WireWriter,
    ): EngineStatus {
        val ordered = try {
            ZipDirectory.read(volume.channel).inReadOrder
        } catch (_: IOException) {
            return EngineStatus.DAMAGED
        }
        for (index in indices.distinct().sorted()) {
            job.throwIfCancelled()
            if (index < 0 || index >= ordered.size) return EngineStatus.PATH_MISMATCH
            val cd = ordered[index.toInt()]
            val name = LocalHeader.name(volume.channel, cd.localHeaderOffset) ?: return EngineStatus.PATH_MISMATCH
            if (expected != null && !name.sameEntryAs(expected[index])) return EngineStatus.PATH_MISMATCH
            out.send { dataStart(index, name); progress(cd.localHeaderOffset) }
            val outcome = copyOne(job, volume.channel, cd, password, out)
            out.send { dataEnd(index, outcome) }
        }
        return EngineStatus.OK
    }

    private fun copyOne(job: EngineJob, channel: FileChannel, cd: ZipCdEntry, password: CharArray?, out: WireWriter): EntryOutcome {
        if (cd.encrypted && password == null) return EntryOutcome.ENCRYPTED
        val method = cd.aes?.actualMethod ?: cd.method
        if (method != METHOD_STORE && method != METHOD_DEFLATE) return EntryOutcome.UNSUPPORTED
        // zip4j takes only these three values and the directory flag from the central directory;
        // everything else (encryption, AES record, method) it reads from the local header itself.
        val header = FileHeader().apply {
            crc = cd.crc
            compressedSize = cd.packedSize
            uncompressedSize = cd.size
            isDirectory = false
        }
        val source = ChannelInput(channel, cd.localHeaderOffset, job)
        val key = password?.copyOf()   // zip4j keeps the array it is given
        return try {
            ZipInputStream(BufferedInputStream(source, EngineWire.CHUNK_BYTES), key).use { zip ->
                // No local header where the directory points: nothing to decrypt.
                if (zip.getNextEntry(header, false) == null) return EntryOutcome.DAMAGED
                EntryPump.copy(zip, EntryPump.limitFor(cd.size), out) { source.position }
            }
        } catch (e: ZipException) {
            when (e.type) {
                ZipException.Type.WRONG_PASSWORD -> EntryOutcome.WRONG_PASSWORD
                ZipException.Type.UNKNOWN_COMPRESSION_METHOD, ZipException.Type.UNSUPPORTED_ENCRYPTION -> EntryOutcome.UNSUPPORTED
                else -> EntryOutcome.DAMAGED
            }
        } catch (_: IOException) {
            EntryOutcome.DAMAGED
        } finally {
            key?.fill(WIPED)
        }
    }

    private fun wireEntry(index: Long, name: RawPath, cd: ZipCdEntry): WireEntry {
        val bytes = name.bytes
        val folder = bytes.isNotEmpty() && bytes.last() == SLASH
        return WireEntry(
            index, name, cd.efs, if (folder) EntryType.DIRECTORY else EntryType.FILE, cd.size, cd.packedSize,
            null, cd.encrypted, cd.crc, null, cd.madeBy,
        )
    }

    private companion object {
        const val METHOD_STORE = 0
        const val METHOD_DEFLATE = 8
        const val SLASH: Byte = 0x2F
        val WIPED = Char(0)
    }
}
