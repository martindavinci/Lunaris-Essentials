package io.github.martindavinci.lunaris.archives.create

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Test
import java.io.File
import java.nio.channels.FileChannel

class ZipWriterTest {
    private fun write(level: CompressionLevel, password: String?, files: Map<String, ByteArray>, knownSizes: Boolean = true): File {
        val f = File.createTempFile("writer", ".zip").apply { deleteOnExit() }
        ZipWriter(f.outputStream(), level, password?.toCharArray()).use { w ->
            files.forEach { (p, b) -> w.add(p, if (knownSizes) b.size.toLong() else null, 0, b.inputStream()) {} }
        }
        return f
    }
    private val repetitive = "lunaris archives ".repeat(500).toByteArray()

    @Test fun normalZip_readsBackWithZip4j() {
        val f = write(CompressionLevel.NORMAL, null, mapOf("a.txt" to "hello".toByteArray(), "dir/b.txt" to "b".toByteArray()))
        ZipFile(f).use { z ->
            assertThat(z.fileHeaders.map { it.fileName }).containsExactly("a.txt", "dir/b.txt")
            assertThat(z.getInputStream(z.getFileHeader("a.txt")).readBytes()).isEqualTo("hello".toByteArray())
        }
    }
    @Test fun storeZip_storesWithoutCompression() {
        ZipFile(write(CompressionLevel.STORE, null, mapOf("a.txt" to repetitive))).use { z ->
            assertThat(z.fileHeaders.single().compressionMethod).isEqualTo(CompressionMethod.STORE)
        }
    }
    @Test fun maximum_deflates() {
        val max = write(CompressionLevel.MAXIMUM, null, mapOf("a.txt" to repetitive))
        val store = write(CompressionLevel.STORE, null, mapOf("a.txt" to repetitive))
        ZipFile(max).use { z -> assertThat(z.fileHeaders.single().compressionMethod).isEqualTo(CompressionMethod.DEFLATE) }
        assertThat(max.length()).isLessThan(store.length())
    }
    @Test fun passwordZip_isAes256_andNeedsThePassword() {
        val f = write(CompressionLevel.NORMAL, "Secret-2026", mapOf("a.txt" to "hello".toByteArray()))
        ZipFile(f).use { z ->
            val h = z.fileHeaders.single()
            assertThat(h.encryptionMethod).isEqualTo(EncryptionMethod.AES)
            assertThat(h.aesExtraDataRecord.aesKeyStrength).isEqualTo(AesKeyStrength.KEY_STRENGTH_256)
        }
        ZipFile(f, "Secret-2026".toCharArray()).use { z -> assertThat(z.getInputStream(z.fileHeaders.single()).readBytes()).isEqualTo("hello".toByteArray()) }
    }
    @Test fun storeWithUnknownSize_fallsBackToDeflate() {
        ZipFile(write(CompressionLevel.STORE, null, mapOf("a.txt" to repetitive), knownSizes = false)).use { z ->
            assertThat(z.fileHeaders.single().compressionMethod).isEqualTo(CompressionMethod.DEFLATE)
        }
    }
    @Test fun ourOwnReaderReadsIt() {
        val f = write(CompressionLevel.NORMAL, null, mapOf("Café.txt" to "x".toByteArray(), "b.txt" to "y".toByteArray()))
        val entries = FileChannel.open(f.toPath()).use { ZipDirectory.read(it).entries }
        assertThat(entries.map { String(it.name.bytes) }).containsExactly("Café.txt", "b.txt")
        assertThat(entries.first().efs).isTrue()
    }
}
