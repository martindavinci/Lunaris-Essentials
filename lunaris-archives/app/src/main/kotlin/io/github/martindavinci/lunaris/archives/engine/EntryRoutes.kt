package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveEntry
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.session.OpenArchive

/** Spec "Instradamento" 2: which reader extracts an entry. */
object EntryRoutes {
    /** Encrypted entries go to the reader that can decrypt them; null for a protected RAR 4 entry. */
    fun routeFor(archive: OpenArchive, entry: ArchiveEntry): EngineRoute? {
        if (!entry.encrypted) return archive.route
        return when (archive.index.kind) {
            ArchiveKind.ZIP -> EngineRoute.ZIP4J
            ArchiveKind.SEVEN_Z -> EngineRoute.SEVEN_Z
            ArchiveKind.RAR5 -> EngineRoute.LIB_RAR
            ArchiveKind.RAR4 -> null
            else -> archive.route
        }
    }
}
