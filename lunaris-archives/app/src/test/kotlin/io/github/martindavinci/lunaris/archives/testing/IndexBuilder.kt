package io.github.martindavinci.lunaris.archives.testing

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveIndex
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.RawPath
import io.github.martindavinci.lunaris.archives.safety.SafePath

private const val TEST_SIZE = 1024L

/** index("docs/", "docs/a.md", "../x") — names ending in "/" are folders. */
fun index(vararg paths: String, kind: ArchiveKind = ArchiveKind.ZIP, encrypted: Set<String> = emptySet()): ArchiveIndex =
    ArchiveIndex(kind, kind.label, paths.mapIndexed { i, p -> entry(i.toLong(), p, encrypted = p in encrypted) },
        Encryption.NONE, comment = null, createdBy = null, cappedAtLimit = false, partial = false)

fun entry(i: Long, p: String, type: EntryType = if (p.endsWith("/")) EntryType.DIRECTORY else EntryType.FILE,
          size: Long? = if (p.endsWith("/")) null else TEST_SIZE, encrypted: Boolean = false, mtime: Long? = 0): ArchiveEntry =
    ArchiveEntry(i, RawPath.of(p.toByteArray()), p, SafePath.evaluate(p, type), type, size, size, mtime, encrypted, crc = i)
