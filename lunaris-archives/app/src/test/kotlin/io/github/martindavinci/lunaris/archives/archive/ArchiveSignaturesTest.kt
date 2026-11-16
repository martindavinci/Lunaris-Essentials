package io.github.martindavinci.lunaris.archives.archive

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.testing.Fixtures
import org.junit.Test

class ArchiveSignaturesTest {
    private fun head(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private fun at(offset: Int, text: String) = ByteArray(offset + text.length).also { text.toByteArray().copyInto(it, offset) }

    @Test fun bytesDecide() {
        assertThat(ArchiveSignatures.detect(Fixtures.bytes("plain.zip"), "x.bin")).isEqualTo(ArchiveKind.ZIP)
        assertThat(ArchiveSignatures.detect(head(0x50, 0x4B, 0x05, 0x06), null)).isEqualTo(ArchiveKind.ZIP)
        assertThat(ArchiveSignatures.detect(head(0x50, 0x4B, 0x07, 0x08), null)).isEqualTo(ArchiveKind.ZIP_SPLIT)
        assertThat(ArchiveSignatures.detect(head(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00), null)).isEqualTo(ArchiveKind.RAR4)
        assertThat(ArchiveSignatures.detect(head(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00), null)).isEqualTo(ArchiveKind.RAR5)
        assertThat(ArchiveSignatures.detect(head(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C), null)).isEqualTo(ArchiveKind.SEVEN_Z)
        assertThat(ArchiveSignatures.detect(Fixtures.bytes("plain.tar.gz"), null)).isEqualTo(ArchiveKind.GZIP)
        assertThat(ArchiveSignatures.detect(head(0x42, 0x5A, 0x68), null)).isEqualTo(ArchiveKind.BZIP2)
        assertThat(ArchiveSignatures.detect(head(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00), null)).isEqualTo(ArchiveKind.XZ)
        assertThat(ArchiveSignatures.detect(head(0x28, 0xB5, 0x2F, 0xFD), null)).isEqualTo(ArchiveKind.ZSTD)
        assertThat(ArchiveSignatures.detect(head(0x04, 0x22, 0x4D, 0x18), null)).isEqualTo(ArchiveKind.LZ4)
        assertThat(ArchiveSignatures.detect(Fixtures.bytes("hostile.tar"), null)).isEqualTo(ArchiveKind.TAR)
        assertThat(ArchiveSignatures.detect(at(32769, "CD001"), null)).isEqualTo(ArchiveKind.ISO9660)
        assertThat(ArchiveSignatures.detect("070701".toByteArray(), null)).isEqualTo(ArchiveKind.CPIO)
        assertThat(ArchiveSignatures.detect(head(0xC7, 0x71), null)).isEqualTo(ArchiveKind.CPIO)
    }
    @Test fun bytesWinOverTheName() {
        assertThat(ArchiveSignatures.detect(Fixtures.bytes("plain.zip"), "report.docx")).isEqualTo(ArchiveKind.ZIP)
    }
    @Test fun nameOnlyWhenBytesSayNothing() {
        assertThat(ArchiveSignatures.detect(ByteArray(16), "Backup.TGZ")).isEqualTo(ArchiveKind.GZIP)
        assertThat(ArchiveSignatures.detect(Fixtures.bytes("not-archive.txt"), "notes.txt")).isEqualTo(ArchiveKind.UNKNOWN)
    }
    @Test fun labels() {
        assertThat(ArchiveSignatures.formatLabel(ArchiveKind.GZIP, innerTar = true)).isEqualTo("TAR.GZ")
        assertThat(ArchiveSignatures.formatLabel(ArchiveKind.GZIP, innerTar = false)).isEqualTo("GZ")
        assertThat(ArchiveSignatures.formatLabel(ArchiveKind.SEVEN_Z, innerTar = false)).isEqualTo("7z")
    }
    @Test fun kindCodes_areStableAndRoundTrip() {
        assertThat(ArchiveKind.entries.map { it.code }).containsNoDuplicates()
        ArchiveKind.entries.forEach { assertThat(ArchiveKind.of(it.code)).isEqualTo(it) }
        assertThat(ArchiveKind.ZIP.code).isEqualTo(0)
        assertThat(ArchiveKind.UNKNOWN.code).isEqualTo(13)
        assertThat(ArchiveKind.of(99)).isEqualTo(ArchiveKind.UNKNOWN)
    }
}
