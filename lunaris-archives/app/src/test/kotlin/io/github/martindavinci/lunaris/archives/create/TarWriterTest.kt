package io.github.martindavinci.lunaris.archives.create

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.random.Random
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TarWriterTest {
    @get:Rule val folder = TemporaryFolder()
    private val big = Random(1).nextBytes(BIG)

    private fun write(format: ArchiveFormat, vararg files: Triple<String, Long?, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        TarWriter(out, format, folder.root).use { tar ->
            files.forEach { (path, size, data) -> tar.add(path, size, MTIME, ByteArrayInputStream(data)) {} }
        }
        return out.toByteArray()
    }

    private fun read(format: ArchiveFormat, bytes: ByteArray): List<Pair<TarArchiveEntry, ByteArray>> {
        val raw: InputStream = ByteArrayInputStream(bytes)
        val inner = when (format) {
            ArchiveFormat.TAR_GZ -> GzipCompressorInputStream(raw)
            ArchiveFormat.TAR_XZ -> XZCompressorInputStream(raw)
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorInputStream(raw)
            else -> raw
        }
        val tar = TarArchiveInputStream(inner, "UTF-8")
        return generateSequence { tar.nextEntry }.map { it to tar.readBytes() }.toList()
    }

    @Test fun eachPacking_roundTrips() {
        listOf(ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_BZ2).forEach { format ->
            val bytes = write(format, Triple("empty.txt", 0L, ByteArray(0)), Triple("naïve/日本.txt", 3L, "abc".toByteArray()),
                Triple("sub/dir/data.bin", BIG.toLong(), big))
            val entries = read(format, bytes)
            assertThat(entries.map { it.first.name }).containsExactly("empty.txt", "naïve/日本.txt", "sub/dir/data.bin").inOrder()
            assertThat(entries[2].second).isEqualTo(big)
            assertThat(entries[1].second).isEqualTo("abc".toByteArray())
            assertThat(entries.map { it.first.modTime.time / MILLIS }).containsExactly(MTIME / MILLIS, MTIME / MILLIS, MTIME / MILLIS)
        }
    }
    @Test fun noUserName_endsUpInTheArchive() {
        val entry = read(ArchiveFormat.TAR, write(ArchiveFormat.TAR, Triple("a.txt", 1L, byteArrayOf(1)))).single().first
        assertThat(entry.userName).isEmpty(); assertThat(entry.groupName).isEmpty()
        assertThat(entry.longUserId).isEqualTo(0); assertThat(entry.mode and MODE_BITS).isEqualTo(FILE_MODE)
    }
    @Test fun unknownSize_isSpooled() {
        val entry = read(ArchiveFormat.TAR, write(ArchiveFormat.TAR, Triple("a.bin", null, big))).single()
        assertThat(entry.first.size).isEqualTo(BIG.toLong()); assertThat(entry.second).isEqualTo(big)
        assertThat(folder.root.listFiles()).isEmpty()
    }
    @Test fun sizeChangedWhileReading_usesSpool() {
        val entry = read(ArchiveFormat.TAR, write(ArchiveFormat.TAR, Triple("a.bin", 10L, ByteArray(20) { 7 }))).single()
        assertThat(entry.first.size).isEqualTo(20); assertThat(entry.second).hasLength(20)
    }
    @Test fun longPath_over100Bytes_isKept() {
        val path = "d/".repeat(60) + "file.txt"
        assertThat(read(ArchiveFormat.TAR, write(ArchiveFormat.TAR, Triple(path, 1L, byteArrayOf(1)))).single().first.name).isEqualTo(path)
    }
    @Test fun readFailure_isSourceReadException() {
        val broken = object : InputStream() { override fun read(): Int = throw IOException("gone") }
        TarWriter(ByteArrayOutputStream(), ArchiveFormat.TAR, folder.root).use { tar ->
            assertThrows(SourceReadException::class.java) { tar.add("a", 1, MTIME, broken) {} }
        }
        assertThat(folder.root.listFiles()).isEmpty()
    }
    @Test fun progress_countsTheBytesRead() {
        var last = 0L
        TarWriter(ByteArrayOutputStream(), ArchiveFormat.TAR, folder.root).use { it.add("a", null, MTIME, ByteArrayInputStream(big)) { n -> last = n } }
        assertThat(last).isEqualTo(BIG.toLong())
    }
    @Test fun zip_isRefused() {
        assertThrows(IllegalArgumentException::class.java) { TarWriter(ByteArrayOutputStream(), ArchiveFormat.ZIP, folder.root) }
    }

    private companion object {
        const val BIG = 300 * 1024
        const val MTIME = 1_793_731_200_000L
        const val MILLIS = 1000
        const val MODE_BITS = 0x1FF
        const val FILE_MODE = 0x1A4   // 0644
    }
}
