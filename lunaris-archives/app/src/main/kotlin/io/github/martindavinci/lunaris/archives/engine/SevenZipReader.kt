package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.archive.SevenZipKdfCheck
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.IOException
import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod

/**
 * Encrypted 7z through Commons Compress 1.28.0.
 *
 * - Wanted entries are read with `getInputStream(entry)` in rising order, not by walking
 *   `nextEntry`: entering a folder builds its whole decoder stack (a dictionary, and for LZMA a
 *   key derivation), and a non-solid archive has one folder per file. This way only the folders
 *   that hold a wanted entry are opened.
 * - Content methods are filled in when a folder is opened, not when the headers are read, so
 *   they cannot tell during a listing whether an entry is encrypted.
 */
class SevenZipReader : IsolatedReader {
    override fun list(job: EngineJob, volume: EngineVolume, password: CharArray?, out: WireWriter): EngineStatus {
        refusal(volume)?.let { return it }
        val headersEncrypted = SevenZipKdfCheck.headersEncrypted(volume.channel)
        return open(job, volume, password, headersEncrypted) { file ->
            val entries = file.entries.toList()
            // This reader lists only what libarchive could not: archives whose headers are encrypted.
            // In those every file with data is encrypted too.
            entries.take(Limits.MAX_ENTRIES).forEachIndexed { i, entry ->
                job.throwIfCancelled()
                out.send { entry(wireEntry(i.toLong(), entry, file.defaultName ?: UNNAMED, encrypted = headersEncrypted && entry.hasStream())) }
            }
            val capped = entries.size > Limits.MAX_ENTRIES
            val encryption = if (headersEncrypted) Encryption.HEADERS else Encryption.NONE
            out.send { archive(WireArchive(ArchiveKind.SEVEN_Z, false, encryption, null, capped, partial = false)) }
            if (capped) EngineStatus.LIMIT_REACHED else EngineStatus.OK
        }
    }

    override fun extract(
        job: EngineJob, volume: EngineVolume, password: CharArray?, indices: LongArray,
        expected: Map<Long, RawPath>?, out: WireWriter,
    ): EngineStatus {
        refusal(volume)?.let { return it }
        val headersEncrypted = SevenZipKdfCheck.headersEncrypted(volume.channel)
        return open(job, volume, password, headersEncrypted) { file ->
            val entries = file.entries.toList()
            for (index in indices.distinct().sorted()) {
                job.throwIfCancelled()
                if (index < 0 || index >= entries.size) return@open EngineStatus.PATH_MISMATCH
                val entry = entries[index.toInt()]
                val name = RawPath.of((entry.name ?: file.defaultName ?: UNNAMED).toByteArray(Charsets.UTF_8))
                if (expected != null && !name.sameEntryAs(expected[index])) return@open EngineStatus.PATH_MISMATCH
                out.send { dataStart(index, name); progress(volume.channel.position()) }
                val outcome = copyOne(file, entry, volume, password != null, out)
                out.send { dataEnd(index, outcome) }
            }
            EngineStatus.OK
        }
    }

    private fun copyOne(file: SevenZFile, entry: SevenZArchiveEntry, volume: EngineVolume, hasPassword: Boolean, out: WireWriter): EntryOutcome {
        if (!entry.hasStream()) return EntryOutcome.OK   // an empty file or a folder: no bytes
        return try {
            file.getInputStream(entry).use { input ->
                EntryPump.copy(input, EntryPump.limitFor(entry.size), out) { volume.channel.position() }
            }
        } catch (_: PasswordRequiredException) {
            EntryOutcome.ENCRYPTED
        } catch (_: MemoryLimitException) {
            EntryOutcome.UNSUPPORTED
        } catch (e: IOException) {
            failure(entry, hasPassword, e.message)
        } catch (e: IllegalStateException) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failure(entry, hasPassword, e.message)
        } catch (_: IllegalArgumentException) {
            failure(entry, hasPassword, null)
        } catch (_: IndexOutOfBoundsException) {
            failure(entry, hasPassword, null)
        }
    }

    /** 7z has no check value for a password: a wrong one shows as broken data in an encrypted folder. */
    private fun failure(entry: SevenZArchiveEntry, hasPassword: Boolean, message: String?): EntryOutcome {
        if (message != null && UNSUPPORTED_MARKS.any { message.contains(it) }) return EntryOutcome.UNSUPPORTED
        val encrypted = entry.contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } ?: hasPassword
        return if (encrypted) EntryOutcome.WRONG_PASSWORD else EntryOutcome.DAMAGED
    }

    /** A visible key derivation above the limit is refused before Commons Compress can start it. */
    private fun refusal(volume: EngineVolume): EngineStatus? =
        SevenZipKdfCheck.cyclesPower(volume.channel)?.takeIf { it > SevenZipKdfCheck.MAX_CYCLES_POWER }?.let { EngineStatus.UNSUPPORTED_FEATURE }

    private inline fun open(
        job: EngineJob, volume: EngineVolume, password: CharArray?, headersEncrypted: Boolean,
        block: (SevenZFile) -> EngineStatus,
    ): EngineStatus {
        val key = password?.copyOf()
        val file = try {
            val builder = SevenZFile.builder()
                .setSeekableByteChannel(CancellableStreams.channel(volume.channel, job))
                .setMaxMemoryLimitKiB(memoryLimitKiB())   // not setMaxMemoryLimitKb: 1.28.0 divides that by 1024
                .setUseDefaultNameForUnnamedEntries(true)
                .setTryToRecoverBrokenArchives(false)
            if (key != null) builder.setPassword(key)
            builder.get()
        } catch (_: PasswordRequiredException) {
            return EngineStatus.HEADERS_ENCRYPTED
        } catch (_: MemoryLimitException) {
            return EngineStatus.OUT_OF_MEMORY
        } catch (_: IOException) {
            return if (headersEncrypted && password != null) EngineStatus.WRONG_PASSWORD else EngineStatus.DAMAGED
        } finally {
            key?.fill(WIPED)   // the builder has turned it into bytes of its own by now
        }
        return file.use { block(it) }
    }

    private fun wireEntry(index: Long, entry: SevenZArchiveEntry, defaultName: String, encrypted: Boolean): WireEntry {
        val name = RawPath.of((entry.name ?: defaultName).toByteArray(Charsets.UTF_8))
        val type = if (entry.isDirectory) EntryType.DIRECTORY else EntryType.FILE
        return WireEntry(
            index, name, true, type, entry.size.takeIf { !entry.isDirectory }, null,
            if (entry.hasLastModifiedDate) entry.lastModifiedTime.toMillis() else null,
            encrypted, if (entry.hasCrc) entry.crcValue else null, null, null,
        )
    }

    private fun memoryLimitKiB(): Int =
        (Runtime.getRuntime().maxMemory() / Limits.MEMORY_SHARE_DIVISOR / BYTES_PER_KIB).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private companion object {
        const val BYTES_PER_KIB = 1024L
        val WIPED = Char(0)
        // Commons Compress has no typed exception for these; the texts are from Coders.addDecoder
        // and SevenZFile.buildDecoderStack of 1.28.0 (PPMd, BCJ2 and other coders it cannot run).
        val UNSUPPORTED_MARKS = listOf("Unsupported compression method", "not yet supported")
        /** Opened from a channel there is no file name to derive one from. */
        const val UNNAMED = "unnamed"
    }
}
