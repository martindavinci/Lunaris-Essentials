package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.Encryption
import io.github.martindavinci.lunaris.archives.archive.EntryType
import org.junit.Test

class LibarchiveCodesTest {
    @Test fun formats() {
        assertThat(LibarchiveCodes.kind(0x50000, intArrayOf(0))).isEqualTo(ArchiveKind.ZIP to false)
        assertThat(LibarchiveCodes.kind(0x50001, intArrayOf(0))).isEqualTo(ArchiveKind.ZIP to false)
        assertThat(LibarchiveCodes.kind(0xE0000, intArrayOf(0))).isEqualTo(ArchiveKind.SEVEN_Z to false)
        assertThat(LibarchiveCodes.kind(0xD0000, intArrayOf(0))).isEqualTo(ArchiveKind.RAR4 to false)
        assertThat(LibarchiveCodes.kind(0x100000, intArrayOf(0))).isEqualTo(ArchiveKind.RAR5 to false)
        assertThat(LibarchiveCodes.kind(0x30000, intArrayOf(1, 0))).isEqualTo(ArchiveKind.GZIP to true)
        assertThat(LibarchiveCodes.kind(0x30000, intArrayOf(0))).isEqualTo(ArchiveKind.TAR to false)
        assertThat(LibarchiveCodes.kind(0x90000, intArrayOf(14, 0))).isEqualTo(ArchiveKind.ZSTD to false)
        assertThat(LibarchiveCodes.kind(0x90000, intArrayOf(0))).isEqualTo(ArchiveKind.UNKNOWN to false)
        assertThat(LibarchiveCodes.kind(0x40000, intArrayOf(0))).isEqualTo(ArchiveKind.ISO9660 to false)
        assertThat(LibarchiveCodes.kind(0x10000, intArrayOf(0))).isEqualTo(ArchiveKind.CPIO to false)
        assertThat(LibarchiveCodes.kind(0x60000, intArrayOf(0))).isEqualTo(ArchiveKind.UNKNOWN to false)
        assertThat(LibarchiveCodes.kind(0x30000, IntArray(0))).isEqualTo(ArchiveKind.TAR to false)
    }
    @Test fun types() {
        assertThat(LibarchiveCodes.entryType(0x8000, false)).isEqualTo(EntryType.FILE)
        assertThat(LibarchiveCodes.entryType(0x8000, true)).isEqualTo(EntryType.HARDLINK)
        assertThat(LibarchiveCodes.entryType(0x4000, false)).isEqualTo(EntryType.DIRECTORY)
        assertThat(LibarchiveCodes.entryType(0xA000, false)).isEqualTo(EntryType.SYMLINK)
        assertThat(LibarchiveCodes.entryType(0x2000, false)).isEqualTo(EntryType.CHAR_DEVICE)
        assertThat(LibarchiveCodes.entryType(0x6000, false)).isEqualTo(EntryType.BLOCK_DEVICE)
        assertThat(LibarchiveCodes.entryType(0x1000, false)).isEqualTo(EntryType.FIFO)
        assertThat(LibarchiveCodes.entryType(0xC000, false)).isEqualTo(EntryType.SOCKET)
        assertThat(LibarchiveCodes.entryType(0, false)).isEqualTo(EntryType.SOCKET)
    }
    @Test fun encryption() {
        assertThat(LibarchiveCodes.encryption(1)).isEqualTo(Encryption.SOME_ENTRIES)
        assertThat(LibarchiveCodes.encryption(0)).isEqualTo(Encryption.NONE)
        assertThat(LibarchiveCodes.encryption(-2)).isEqualTo(Encryption.NONE)
    }
}
