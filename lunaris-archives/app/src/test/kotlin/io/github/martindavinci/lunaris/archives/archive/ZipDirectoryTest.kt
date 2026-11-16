package io.github.martindavinci.lunaris.archives.archive

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.testing.Fixtures
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.channels.FileChannel

class ZipDirectoryTest {
    private fun read(name: String) = FileChannel.open(Fixtures.file(name).toPath()).use { ZipDirectory.read(it) }
    private fun names(list: List<ZipCdEntry>) = list.map { String(it.name.bytes) }

    @Test fun plainZip_listsEveryEntryInOrderWithSizes() {
        val dir = read("plain.zip")
        assertThat(names(dir.entries)).containsExactly(
            "README.md", "docs/architecture.md", "docs/meeting-2026-10-30.md", "img/cover.png", "budget.csv", "file2.txt", "file10.txt").inOrder()
        val readme = dir.entries.first()
        assertThat(readme.size).isEqualTo(16); assertThat(readme.method).isEqualTo(8); assertThat(readme.encrypted).isFalse()
        assertThat(readme.localHeaderOffset).isEqualTo(0)
        assertThat(String(dir.comment!!)).isEqualTo("Notes and drafts for the November review.")
    }
    @Test fun zip64Sizes_areRead() {
        val e = read("zip64.zip").entries.single()
        assertThat(e.size).isEqualTo(1000)
    }
    @Test fun duplicateNames_keepTheirOrder() {
        val dups = read("hostile.zip").entries.filter { String(it.name.bytes) == "dup.txt" }
        assertThat(dups.map { it.order }).isInOrder()
        assertThat(dups).hasSize(2)
    }
    @Test fun backslashNames_areKeptAsWritten() {
        assertThat(names(read("hostile.zip").entries)).containsAtLeast("\\..\\x", "..\\..\\x", "C:\\x")
    }
    @Test fun cp437Name_hasNoEfsFlag() { assertThat(read("cp437.zip").entries.single().efs).isFalse() }
    @Test fun readOrder_followsTheLocalHeaders() {
        val dir = read("reordered.zip")
        assertThat(names(dir.entries)).containsExactly("third.txt", "second.txt", "first.txt").inOrder()
        assertThat(names(dir.inReadOrder)).containsExactly("first.txt", "second.txt", "third.txt").inOrder()
    }
    @Test fun localHeaderName_isReadAtItsOffset() {
        FileChannel.open(Fixtures.file("reordered.zip").toPath()).use { channel ->
            val second = ZipDirectory.read(channel).inReadOrder[1]
            assertThat(LocalHeader.name(channel, second.localHeaderOffset)).isEqualTo(RawPath.of("second.txt".toByteArray()))
            assertThat(LocalHeader.name(channel, second.localHeaderOffset + 1)).isNull()
            assertThat(LocalHeader.name(channel, channel.size())).isNull()
        }
    }
    @Test fun truncatedFile_throws() {
        val f = File.createTempFile("cut", ".zip").apply { writeBytes(Fixtures.bytes("plain.zip").copyOf(40)); deleteOnExit() }
        assertThrows(ZipDirectoryException::class.java) { FileChannel.open(f.toPath()).use { ZipDirectory.read(it) } }
    }
    @Test fun notAZip_throws() { assertThrows(ZipDirectoryException::class.java) { read("not-archive.txt") } }
}
