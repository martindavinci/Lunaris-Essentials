package io.github.martindavinci.lunaris.archives.archive

/** Everything the browser knows about an open archive. */
data class ArchiveIndex(
    val kind: ArchiveKind, val formatLabel: String, val entries: List<ArchiveEntry>, val encryption: Encryption,
    val comment: String?, val createdBy: HostOs?, val cappedAtLimit: Boolean, val partial: Boolean,
)
