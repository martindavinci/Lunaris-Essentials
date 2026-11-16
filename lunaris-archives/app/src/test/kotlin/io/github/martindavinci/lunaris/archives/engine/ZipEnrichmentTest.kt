package io.github.martindavinci.lunaris.archives.engine

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ArchiveKind
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import io.github.martindavinci.lunaris.archives.testing.Fixtures
import org.junit.Test
import java.nio.channels.FileChannel

class ZipEnrichmentTest {
    /** Entries as libarchive lists them: by local header, with no sizes beyond the plain one. */
    private fun libarchiveLike(name: String) = FileChannel.open(Fixtures.file(name).toPath()).use { ch ->
        ZipDirectory.read(ch).inReadOrder.mapIndexed { i, cd ->
            val type = if (String(cd.name.bytes).endsWith("/")) EntryType.DIRECTORY else EntryType.FILE
            WireEntry(i.toLong(), cd.name, cd.efs, type, cd.size, null, null, false, null, null, null)
        }
    }
    private fun enrich(name: String, kind: ArchiveKind = ArchiveKind.ZIP, entries: List<WireEntry> = libarchiveLike(name)) =
        FileChannel.open(Fixtures.file(name).toPath()).use { ZipEnrichment.apply(kind, EngineVolume(-1, it), entries) }

    @Test fun addsPackedSizeCrcHostAndComment() {
        val r = enrich("plain.zip")
        assertThat(r.entries.first().packedSize).isNotNull(); assertThat(r.entries.first().crc).isNotNull(); assertThat(r.entries.first().host).isNotNull()
        assertThat(String(r.comment!!)).isEqualTo("Notes and drafts for the November review.")
    }
    @Test fun cp437Name_staysNotUtf8() { assertThat(enrich("cp437.zip").entries.single().pathIsUtf8).isFalse() }
    @Test fun duplicates_eachGetTheirOwnCrc() {
        val dups = enrich("hostile.zip").entries.filter { String(it.rawPath.bytes) == "dup.txt" }
        assertThat(dups.map { it.crc }.distinct()).hasSize(2)
    }
    @Test fun aReorderedDirectory_pairsByListingPlace() {
        val r = enrich("reordered.zip")
        val crcs = FileChannel.open(Fixtures.file("reordered.zip").toPath()).use { ch -> ZipDirectory.read(ch).inReadOrder.map { it.crc } }
        assertThat(r.entries.map { it.crc }).isEqualTo(crcs)
    }
    @Test fun encryptionFromTheDirectory_isKept() {
        assertThat(enrich("aes.zip").entries.filter { it.type == EntryType.FILE }.all { it.encrypted }).isTrue()
    }
    @Test fun nonZip_isUntouched() {
        val e = libarchiveLike("plain.zip")
        assertThat(enrich("plain.zip", kind = ArchiveKind.TAR, entries = e).entries).isEqualTo(e)
    }
    @Test fun brokenDirectory_keepsLibarchiveEntries() {
        val e = libarchiveLike("plain.zip")
        val r = enrich("not-archive.txt", entries = e)
        assertThat(r.entries).isEqualTo(e)
        assertThat(r.comment).isNull()
    }
}
