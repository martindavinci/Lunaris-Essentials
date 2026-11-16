package io.github.martindavinci.lunaris.archives.engine

import eu.darken.rar.RarChecksum
import eu.darken.rar.RarCorruptException
import eu.darken.rar.RarEntry
import eu.darken.rar.RarException
import eu.darken.rar.RarHostOs
import eu.darken.rar.RarLimits
import eu.darken.rar.RarMissingVolumeException
import eu.darken.rar.RarOptions
import eu.darken.rar.RarPasswordProvider
import eu.darken.rar.RarPasswordRequiredException
import eu.darken.rar.RarPasswordScope
import eu.darken.rar.RarReader
import eu.darken.rar.RarRedirectionType
import eu.darken.rar.RarUnsupportedException
import eu.darken.rar.RarUnsupportedReason
import eu.darken.rar.RarWrongPasswordException
import eu.darken.rar.mtimeMillis
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.HostOs
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.Limits

/**
 * RAR5 with a password, or with a dictionary libarchive refuses, through Butler's lib-rar
 * (API checked against commit 686c6c0).
 * - the 200 000 cap is ours, in the loop: as lib-rar's `maxEntries` it would turn a big archive
 *   into "unsupported" instead of a capped list;
 * - the reader gets [ChannelInput]: lib-rar closes what it is given and skips over data it does
 *   not need;
 * - a listing that breaks half-way still sends its ARCHIVE message, so the main process can show
 *   what was read.
 */
class RarReader5 : IsolatedReader {
    override fun list(job: EngineJob, volume: EngineVolume, password: CharArray?, out: WireWriter): EngineStatus {
        var headersEncrypted = false
        var capped = false
        val status = guarded {
            RarReader(ChannelInput(volume.channel, 0, job), options(job, password, listOnly = true)).use { reader ->
                headersEncrypted = reader.archive.headerEncrypted
                var count = 0
                while (true) {
                    val entry = reader.nextEntry() ?: break
                    if (count++ >= Limits.MAX_ENTRIES) { capped = true; break }
                    out.send { entry(wireEntry(entry)) }
                }
            }
            if (capped) EngineStatus.LIMIT_REACHED else EngineStatus.OK
        }
        val encryption = if (headersEncrypted) Encryption.HEADERS else Encryption.NONE
        out.send { archive(WireArchive(ArchiveKind.RAR5, false, encryption, null, capped, partial = status == EngineStatus.DAMAGED)) }
        return status
    }

    override fun extract(
        job: EngineJob, volume: EngineVolume, password: CharArray?, indices: LongArray,
        expected: Map<Long, RawPath>?, out: WireWriter,
    ): EngineStatus = guarded {
        val wanted = indices.distinct().sorted()
        val input = ChannelInput(volume.channel, 0, job)
        RarReader(input, options(job, password, listOnly = false)).use { reader ->
            var next = 0
            while (next < wanted.size) {
                val entry = reader.nextEntry() ?: return@guarded EngineStatus.PATH_MISMATCH
                val index = entry.ordinal.toLong()
                if (index < wanted[next]) continue
                if (index > wanted[next]) return@guarded EngineStatus.PATH_MISMATCH
                next++
                val name = RawPath.of(entry.name.toByteArray(Charsets.UTF_8))
                if (expected != null && expected[index] != name) return@guarded EngineStatus.PATH_MISMATCH
                out.send { dataStart(index, name); progress(input.position) }
                val outcome = copyOne(reader, entry, out) { input.position }
                out.send { dataEnd(index, outcome) }
            }
        }
        EngineStatus.OK
    }

    private fun copyOne(reader: RarReader, entry: RarEntry, out: WireWriter, position: () -> Long): EntryOutcome = try {
        EntryPump.copy(reader.entryStream(), EntryPump.limitFor(entry.size), out, position)
    } catch (_: RarPasswordRequiredException) {
        EntryOutcome.ENCRYPTED
    } catch (_: RarWrongPasswordException) {
        EntryOutcome.WRONG_PASSWORD
    } catch (_: RarUnsupportedException) {
        EntryOutcome.UNSUPPORTED
    } catch (_: RarCorruptException) {
        EntryOutcome.DAMAGED   // the reader is unusable from here on: the next call ends the pass
    }

    /** What lib-rar throws outside one entry's data, as the status of the whole call. */
    private inline fun guarded(block: () -> EngineStatus): EngineStatus = try {
        block()
    } catch (e: RarPasswordRequiredException) {
        if (e.scope == RarPasswordScope.HEADERS) EngineStatus.HEADERS_ENCRYPTED else EngineStatus.DATA_ENCRYPTED
    } catch (_: RarWrongPasswordException) {
        EngineStatus.WRONG_PASSWORD
    } catch (e: RarUnsupportedException) {
        when (e.reason) {
            is RarUnsupportedReason.Rar4 -> EngineStatus.RAR4_ENCRYPTED   // only a password sends a RAR4 here
            is RarUnsupportedReason.DictionaryTooLarge -> EngineStatus.OUT_OF_MEMORY
            else -> EngineStatus.UNSUPPORTED_FEATURE
        }
    } catch (_: RarMissingVolumeException) {
        EngineStatus.NEEDS_NEXT_VOLUME
    } catch (_: RarException) {
        EngineStatus.DAMAGED
    }

    private fun options(job: EngineJob, password: CharArray?, listOnly: Boolean) = RarOptions(
        // lib-rar wipes every array it is handed: give it a copy each time it asks.
        passwordProvider = password?.let { key -> RarPasswordProvider { key.copyOf() } },
        limits = RarLimits(
            maxWindowBytes = minOf(Limits.RAR_MAX_WINDOW_BYTES, Runtime.getRuntime().maxMemory() / Limits.MEMORY_SHARE_DIVISOR),
            maxKdfLog2 = Limits.KDF_MAX_LOG2,
        ),
        listOnly = listOnly,
        cancellation = { job.throwIfCancelled() },
    )

    private fun wireEntry(entry: RarEntry): WireEntry {
        val redirection = entry.redirection
        val type = when {
            entry.isDirectory -> EntryType.DIRECTORY
            redirection == null -> EntryType.FILE
            redirection.type == RarRedirectionType.HARDLINK || redirection.type == RarRedirectionType.FILE_COPY -> EntryType.HARDLINK
            else -> EntryType.SYMLINK
        }
        val host = when (entry.hostOs) {
            RarHostOs.WINDOWS -> HostOs.WINDOWS
            RarHostOs.UNIX -> HostOs.UNIX
            RarHostOs.OTHER -> HostOs.OTHER
        }
        val crc = (entry.checksum as? RarChecksum.Crc32)?.value?.toLong()?.and(CRC_MASK)
        return WireEntry(
            entry.ordinal.toLong(), RawPath.of(entry.name.toByteArray(Charsets.UTF_8)), true, type, entry.size,
            entry.packedSize, entry.mtimeMillis, entry.isEncrypted, crc,
            redirection?.let { RawPath.of(it.target.toByteArray(Charsets.UTF_8)) }, host,
        )
    }

    private companion object { const val CRC_MASK = 0xFFFFFFFFL }
}
