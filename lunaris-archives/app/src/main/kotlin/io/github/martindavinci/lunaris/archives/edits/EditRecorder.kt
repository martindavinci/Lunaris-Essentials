package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.files.CopyRecorder
import io.github.martindavinci.lunaris.archives.session.ArchiveSource
import io.github.martindavinci.lunaris.archives.session.OpenArchive
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import javax.inject.Inject

/** What identifies an archive file whatever its URI. */
data class ArchiveFacts(val size: Long, val mtime: Long?, val fingerprint: String)

fun interface ArchiveProbe {
    fun probe(source: ArchiveSource): ArchiveFacts?
}

/**
 * Through the archive's own descriptor; null when it can no longer be read. The size is the file's, never the
 * provider's column: right after an update a provider can still report the old one.
 */
class DescriptorProbe @Inject constructor() : ArchiveProbe {
    override fun probe(source: ArchiveSource): ArchiveFacts? = runCatching {
        source.openFd().use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                val size = channel.size()
                ArchiveFacts(size, source.modifiedMillis, Fingerprint.of(channel, size))
            }
        }
    }.getOrNull()
}

/**
 * Remembers a copy handed to another app with the right to write. Archives inside archives, copies of archives,
 * and archives whose names are hidden stay read-only: none of them could be updated, and the last would leave
 * hidden names on disk.
 */
class EditRecorder(private val store: EditStore, private val probe: ArchiveProbe, private val clock: () -> Long) : CopyRecorder {
    @Inject constructor(store: EditStore, probe: DescriptorProbe) : this(store, probe, System::currentTimeMillis)

    override suspend fun record(archive: OpenArchive, entry: ArchiveEntry, copy: File): Boolean {
        val source = archive.source
        if (archive.parentId != null || source.fromCopy || archive.index.encryption == Encryption.HEADERS) return false
        if (store.all().any { it.copyPath == copy.path }) return true   // handed out before: no probe, no hash of a big copy again
        val facts = probe.probe(source) ?: return false
        store.put(
            EditRecord(
                id = UUID.randomUUID().toString(), copyPath = copy.path, archiveUri = source.uri, archiveKey = source.key,
                archiveName = source.name, archiveSize = facts.size, archiveMtime = facts.mtime, archiveFingerprint = facts.fingerprint,
                archiveFormat = archive.index.kind.name, entryPath = entry.displayPath, size = copy.length(), mtime = copy.lastModified(),
                sha256 = Fingerprint.sha256(copy), updatable = archive.index.kind == ArchiveKind.ZIP, createdMillis = clock(),
                entryRaw = entry.rawPath.bytes.joinToString("") { "%02x".format(it) },
            ),
        )
        return true
    }
}
