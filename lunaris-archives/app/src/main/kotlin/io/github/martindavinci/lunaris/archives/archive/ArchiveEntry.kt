package io.github.martindavinci.lunaris.archives.archive

import io.github.martindavinci.lunaris.archives.safety.SafePath

/** How much of an archive is encrypted; [code] travels on the wire. */
enum class Encryption(val code: Int) {
    NONE(0), SOME_ENTRIES(1), ALL_ENTRIES(2), HEADERS(3);

    companion object {
        fun of(code: Int): Encryption = entries.first { it.code == code }
    }
}

/** The system an archive says it was made on; [code] travels on the wire. */
enum class HostOs(val code: Int) {
    MSDOS(0), UNIX(1), MACOS(2), WINDOWS(3), OTHER(4);

    companion object {
        fun of(code: Int): HostOs = entries.first { it.code == code }
    }
}

/** One entry of an index; [path] is the verdict of the path rules on [displayPath]. */
data class ArchiveEntry(
    val index: Long, val rawPath: RawPath, val displayPath: String, val path: SafePath.Result,
    val type: EntryType, val size: Long?, val packedSize: Long?, val modifiedMillis: Long?,
    val encrypted: Boolean, val crc: Long?, val pathIsUtf8: Boolean = true,
)
