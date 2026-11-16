package io.github.martindavinci.lunaris.archives.archive

/** A container or filter format. [code] travels on the wire and in the index cache: never reuse one. */
enum class ArchiveKind(val code: Int, val label: String) {
    ZIP(0, "ZIP"), SEVEN_Z(1, "7z"), RAR4(2, "RAR"), RAR5(3, "RAR"), TAR(4, "TAR"), GZIP(5, "GZ"),
    BZIP2(6, "BZ2"), XZ(7, "XZ"), ZSTD(8, "ZST"), LZ4(9, "LZ4"), ISO9660(10, "ISO"), CPIO(11, "CPIO"),
    ZIP_SPLIT(12, "ZIP"), UNKNOWN(13, "");

    companion object {
        fun of(code: Int): ArchiveKind = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
