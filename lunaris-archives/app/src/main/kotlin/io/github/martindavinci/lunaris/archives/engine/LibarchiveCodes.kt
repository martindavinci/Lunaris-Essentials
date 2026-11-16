package io.github.martindavinci.lunaris.archives.engine

import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType

/** libarchive's numbers (archive.h, archive_entry.h of 3.8.11) as the app's types. */
object LibarchiveCodes {
    private const val FORMAT_BASE_MASK = 0xff0000
    private const val FORMAT_CPIO = 0x10000
    private const val FORMAT_TAR = 0x30000
    private const val FORMAT_ISO9660 = 0x40000
    private const val FORMAT_ZIP = 0x50000
    private const val FORMAT_RAW = 0x90000
    private const val FORMAT_RAR = 0xD0000
    private const val FORMAT_7ZIP = 0xE0000
    private const val FORMAT_RAR_V5 = 0x100000
    private const val FILTER_GZIP = 1
    private const val FILTER_BZIP2 = 2
    private const val FILTER_XZ = 6
    private const val FILTER_LZ4 = 13
    private const val FILTER_ZSTD = 14
    private const val AE_IFREG = 0x8000
    private const val AE_IFDIR = 0x4000
    private const val AE_IFLNK = 0xA000
    private const val AE_IFCHR = 0x2000
    private const val AE_IFBLK = 0x6000
    private const val AE_IFIFO = 0x1000
    private const val HAS_ENCRYPTED = 1

    private val CONTAINERS = mapOf(
        FORMAT_CPIO to ArchiveKind.CPIO, FORMAT_TAR to ArchiveKind.TAR, FORMAT_ISO9660 to ArchiveKind.ISO9660,
        FORMAT_ZIP to ArchiveKind.ZIP, FORMAT_RAR to ArchiveKind.RAR4, FORMAT_7ZIP to ArchiveKind.SEVEN_Z,
        FORMAT_RAR_V5 to ArchiveKind.RAR5,
    )
    private val FILTERS = mapOf(
        FILTER_GZIP to ArchiveKind.GZIP, FILTER_BZIP2 to ArchiveKind.BZIP2, FILTER_XZ to ArchiveKind.XZ,
        FILTER_LZ4 to ArchiveKind.LZ4, FILTER_ZSTD to ArchiveKind.ZSTD,
    )

    /** The kind to show, and whether a tar is inside the filter ("TAR.GZ"). */
    fun kind(format: Int, filters: IntArray): Pair<ArchiveKind, Boolean> {
        val base = format and FORMAT_BASE_MASK
        val filter = filters.firstOrNull()?.let(FILTERS::get)
        return when {
            base == FORMAT_TAR && filter != null -> filter to true
            base == FORMAT_RAW -> (filter ?: ArchiveKind.UNKNOWN) to false
            else -> (CONTAINERS[base] ?: ArchiveKind.UNKNOWN) to false
        }
    }

    /** "Some" at most: the main process tells "all" from the entries. */
    fun encryption(hasEncrypted: Int): Encryption = if (hasEncrypted == HAS_ENCRYPTED) Encryption.SOME_ENTRIES else Encryption.NONE

    /** A type the app does not know is treated as a device file, which is never written. */
    fun entryType(fileType: Int, isHardlink: Boolean): EntryType = when {
        isHardlink -> EntryType.HARDLINK
        fileType == AE_IFREG -> EntryType.FILE
        fileType == AE_IFDIR -> EntryType.DIRECTORY
        fileType == AE_IFLNK -> EntryType.SYMLINK
        fileType == AE_IFCHR -> EntryType.CHAR_DEVICE
        fileType == AE_IFBLK -> EntryType.BLOCK_DEVICE
        fileType == AE_IFIFO -> EntryType.FIFO
        else -> EntryType.SOCKET
    }
}
