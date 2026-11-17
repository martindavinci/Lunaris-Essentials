package io.github.martindavinci.lunaris.archives.create

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tukaani.xz.LZMA2Options

class SevenZWriterTest {
    @get:Rule val folder = TemporaryFolder()
    private val text = "Day plan, bookings and notes.\n".repeat(4000).toByteArray()

    private fun write(level: CompressionLevel, files: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        SevenZWriter(out, level, folder.root).use { w -> files.forEach { (p, d) -> w.add(p, d.size.toLong(), MTIME, ByteArrayInputStream(d)) {} } }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray): SevenZFile {
        val file = File(folder.newFolder(), "r.7z").apply { writeBytes(bytes) }
        return SevenZFile.builder().setFile(file).get()
    }

    @Test fun eachLevel_roundTrips() {
        CompressionLevel.entries.forEach { level ->
            read(write(level, listOf("notes/日本.txt" to text, "empty.txt" to ByteArray(0)))).use { z ->
                val first = z.nextEntry
                assertThat(first.name).isEqualTo("notes/日本.txt")
                assertThat(z.getInputStream(first).readBytes()).isEqualTo(text)
                assertThat(first.lastModifiedDate.time).isEqualTo(MTIME)
                val method = first.contentMethods.first().method
                assertThat(method).isEqualTo(if (level == CompressionLevel.STORE) SevenZMethod.COPY else SevenZMethod.LZMA2)
                assertThat(z.nextEntry.name).isEqualTo("empty.txt")
            }
        }
    }
    @Test fun close_copiesIntoTheTarget_andLeavesNoWorkFile() {
        var finished = 0L
        val out = ByteArrayOutputStream()
        SevenZWriter(out, CompressionLevel.NORMAL, folder.root) { copied, _ -> finished = copied }.use { it.add("a.txt", text.size.toLong(), MTIME, ByteArrayInputStream(text)) {} }
        assertThat(out.size().toLong()).isEqualTo(finished)
        assertThat(folder.root.listFiles()).isEmpty()
    }
    @Test fun failure_midway_deletesTheWorkFile_andWritesNothing() {
        val broken = object : InputStream() { override fun read(): Int = throw IOException("gone") }
        val out = ByteArrayOutputStream()
        SevenZWriter(out, CompressionLevel.NORMAL, folder.root).use { w ->
            assertThrows(SourceReadException::class.java) { w.add("a", 1, MTIME, broken) {} }
        }
        assertThat(out.size()).isEqualTo(0)
        assertThat(folder.root.listFiles()).isEmpty()
    }
    @Test fun cancelled7z_leavesNoWorkFile() {
        val out = ByteArrayOutputStream()
        SevenZWriter(out, CompressionLevel.NORMAL, folder.root).use { w ->
            w.add("a.txt", text.size.toLong(), MTIME, ByteArrayInputStream(text)) {}
            w.abort()
        }
        assertThat(out.size()).isEqualTo(0)
        assertThat(folder.root.listFiles()).isEmpty()
    }
    @Test fun maximumPreset_encoderMemory_isWithinBudget() {
        assertThat(LZMA2Options(Limits.SEVEN_Z_MAXIMUM_PRESET).encoderMemoryUsage).isAtMost(Limits.LZMA2_ENCODER_BUDGET_KIB)
    }
    @Test fun manySmallFiles_finishQuickly() {
        val started = System.nanoTime()
        write(CompressionLevel.MAXIMUM, List(SMALL_FILES) { "f$it.txt" to ByteArray(SMALL_SIZE) { b -> (b % 7).toByte() } })
        assertThat((System.nanoTime() - started) / NANOS_PER_MILLI).isLessThan(MAX_MILLIS)
    }

    private companion object {
        const val MTIME = 1_793_731_200_000L
        const val SMALL_FILES = 2000
        const val SMALL_SIZE = 1024
        const val NANOS_PER_MILLI = 1_000_000L
        const val MAX_MILLIS = 10_000L
    }
}
