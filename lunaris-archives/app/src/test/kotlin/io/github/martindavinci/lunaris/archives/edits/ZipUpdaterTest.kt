package io.github.martindavinci.lunaris.archives.edits

import com.google.common.truth.Truth.assertThat
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import io.github.martindavinci.lunaris.archives.safety.Limits
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipUpdaterTest {
    @get:Rule val temp = TemporaryFolder()
    private val io = FileUpdateIo()
    private val cache by lazy { temp.newFolder("cache") }
    private val files by lazy { temp.newFolder("files") }
    private var free = Long.MAX_VALUE
    private val updater by lazy { ZipUpdater(io, UpdateJournal(files), cache) { free } }
    private val password = "Lunaris-2026".toCharArray()
    private val other = ByteArray(40_000) { (it % 251).toByte() }

    private class Item(val name: String, val data: ByteArray, val method: CompressionMethod = CompressionMethod.DEFLATE,
                       val encryption: EncryptionMethod? = null, val strength: AesKeyStrength = AesKeyStrength.KEY_STRENGTH_256)

    private fun zip(vararg items: Item): File {
        val file = File(temp.newFolder(), "a.zip")
        ZipOutputStream(file.outputStream(), password).use { out ->
            items.forEach { item ->
                out.putNextEntry(ZipParameters().apply {
                    fileNameInZip = item.name; compressionMethod = item.method; entrySize = item.data.size.toLong()
                    item.encryption?.let { isEncryptFiles = true; encryptionMethod = it; aesKeyStrength = item.strength }
                })
                out.write(item.data); out.closeEntry()
            }
        }
        return file
    }

    private fun record(archive: File, name: String, text: String, raw: ByteArray = name.toByteArray()): EditRecord {
        val facts = io.facts("file:${archive.path}")!!
        val snap = File(temp.newFolder(), name.substringAfterLast('/')).apply { writeText(text); setLastModified(1_793_731_200_000L) }
        return EditRecord(
            "id-$name", "/cache/$name", "file:${archive.path}", "key", "a.zip", facts.size, facts.mtime, facts.fingerprint, "ZIP", name,
            1, 1, "00", updatable = true, createdMillis = 1, pending = Snapshot(snap.path, snap.length(), snap.lastModified(), Fingerprint.sha256(snap)),
            entryRaw = raw.joinToString("") { "%02x".format(it) },
        )
    }

    private fun content(archive: File, name: String, pw: CharArray? = password): String =
        ZipFile(archive, pw).use { z -> z.getInputStream(z.getFileHeader(name)).use { String(it.readBytes()) } }

    private fun cd(archive: File) = RandomAccessFile(archive, "r").use { ZipDirectory.read(it.channel) }

    private fun rawData(archive: File, name: String): ByteArray {
        val e = cd(archive).entries.single { String(it.name.bytes) == name }
        val head = RandomAccessFile(archive, "r").use { raf ->
            raf.seek(e.localHeaderOffset + 26); val n = raf.read() or (raf.read() shl 8); val m = raf.read() or (raf.read() shl 8)
            ByteArray(e.packedSize.toInt()).also { raf.seek(e.localHeaderOffset + 30 + n + m); raf.readFully(it) }
        }
        return head
    }

    private fun update(archive: File, vararg records: EditRecord, pw: CharArray? = password.copyOf()) =
        runBlocking { updater.update("file:${archive.path}", records.toList(), pw) }

    @Test fun plainDeflateEntry_isReplaced_othersKeepTheirBytes() {
        val a = zip(Item("notes.md", "old".toByteArray()), Item("photo.bin", other))
        val before = rawData(a, "photo.bin")
        assertThat(update(a, record(a, "notes.md", "new text"))).isInstanceOf(UpdateResult.Done::class.java)
        assertThat(content(a, "notes.md")).isEqualTo("new text")
        assertThat(rawData(a, "photo.bin")).isEqualTo(before)
        assertThat(cd(a).entries).hasSize(2)
    }
    @Test fun storeEntry_staysStored_andReadsBackWithJavaZipInputStream() {
        val a = zip(Item("notes.md", "old".toByteArray(), CompressionMethod.STORE))
        update(a, record(a, "notes.md", "stored text"))
        assertThat(cd(a).entries.single().method).isEqualTo(0)
        ZipInputStream(a.inputStream()).use { z -> z.nextEntry; assertThat(String(z.readBytes())).isEqualTo("stored text") }
    }
    @Test fun aesEntry_withPassword_isReEncryptedAes256() {
        val a = zip(Item("s.md", "old".toByteArray(), encryption = EncryptionMethod.AES))
        update(a, record(a, "s.md", "secret 2"))
        val h = ZipFile(a, password).use { it.getFileHeader("s.md") }
        assertThat(h.encryptionMethod).isEqualTo(EncryptionMethod.AES)
        assertThat(h.aesExtraDataRecord.aesKeyStrength).isEqualTo(AesKeyStrength.KEY_STRENGTH_256)
        assertThat(content(a, "s.md")).isEqualTo("secret 2")
    }
    @Test fun aes128Entry_staysAes128() {
        val a = zip(Item("s.md", "old".toByteArray(), encryption = EncryptionMethod.AES, strength = AesKeyStrength.KEY_STRENGTH_128))
        update(a, record(a, "s.md", "secret 2"))
        assertThat(ZipFile(a, password).use { it.getFileHeader("s.md") }.aesExtraDataRecord.aesKeyStrength).isEqualTo(AesKeyStrength.KEY_STRENGTH_128)
    }
    @Test fun zipCryptoEntry_staysZipCrypto() {
        val a = zip(Item("s.md", "old".toByteArray(), encryption = EncryptionMethod.ZIP_STANDARD))
        update(a, record(a, "s.md", "secret 2"))
        assertThat(ZipFile(a, password).use { it.getFileHeader("s.md") }.encryptionMethod).isEqualTo(EncryptionMethod.ZIP_STANDARD)
        assertThat(content(a, "s.md")).isEqualTo("secret 2")
    }
    @Test fun twoEntries_inOnePass() {
        val a = zip(Item("a.md", "1".toByteArray()), Item("b.md", "2".toByteArray()), Item("c.md", "3".toByteArray()))
        update(a, record(a, "a.md", "one"), record(a, "c.md", "three"))
        assertThat(listOf("a.md", "b.md", "c.md").map { content(a, it) }).containsExactly("one", "2", "three").inOrder()
    }
    @Test fun encryptedEntry_withoutPassword_needsPassword_andNothingChanges() {
        val a = zip(Item("s.md", "old".toByteArray(), encryption = EncryptionMethod.AES))
        val bytes = a.readBytes()
        assertThat(update(a, record(a, "s.md", "x"), pw = null)).isEqualTo(UpdateResult.NeedsPassword)
        assertThat(a.readBytes()).isEqualTo(bytes)
    }
    @Test fun wrongPassword_isReported_andNothingChanges() {
        listOf(EncryptionMethod.AES, EncryptionMethod.ZIP_STANDARD).forEach { method ->
            val a = zip(Item("s.md", "old secret text".toByteArray(), encryption = method))
            val bytes = a.readBytes()
            assertThat(update(a, record(a, "s.md", "x"), pw = "wrong".toCharArray())).isEqualTo(UpdateResult.WrongPassword)
            assertThat(a.readBytes()).isEqualTo(bytes)
        }
    }
    @Test fun archiveChangedSinceOpened_isRefused() {
        val a = zip(Item("notes.md", "old".toByteArray()))
        val r = record(a, "notes.md", "new")
        a.appendBytes(byteArrayOf(0))
        assertThat(update(a, r)).isEqualTo(UpdateResult.ArchiveChanged)
    }
    @Test fun notEnoughSpace_leavesOriginalUntouched() {
        val a = zip(Item("notes.md", "old".toByteArray()))
        val bytes = a.readBytes(); free = 10
        assertThat(update(a, record(a, "notes.md", "new"))).isEqualTo(UpdateResult.NoSpace)
        assertThat(a.readBytes()).isEqualTo(bytes)
    }
    @Test fun spaceCheck_countsZip4jsOwnCopyToo() {
        val a = zip(Item("notes.md", "old".toByteArray()), Item("photo.bin", other))
        free = a.length() * 2 + 2 * 3 + Limits.CACHE_MARGIN_BYTES
        assertThat(update(a, record(a, "notes.md", "new"))).isEqualTo(UpdateResult.NoSpace)
    }
    @Test fun aProviderThatRefusesTheReplace_leavesTheOriginal_andSaysNotUpdated() {
        val a = zip(Item("notes.md", "old".toByteArray()))
        val bytes = a.readBytes()
        io.alwaysFail = true
        assertThat(update(a, record(a, "notes.md", "new"))).isEqualTo(UpdateResult.Failed)
        assertThat(a.readBytes()).isEqualTo(bytes); assertThat(File(files, "update").listFiles().orEmpty()).isEmpty()
    }
    @Test fun aWriteThatCannotLastARestart_isNotWritable() {
        val a = zip(Item("notes.md", "old".toByteArray()))
        io.access = Access.NONE
        assertThat(update(a, record(a, "notes.md", "new"))).isEqualTo(UpdateResult.NotWritable)
    }
    @Test fun notWritable_isReported() {
        val a = zip(Item("notes.md", "old".toByteArray()))
        io.writable = false
        assertThat(update(a, record(a, "notes.md", "new"))).isEqualTo(UpdateResult.NotWritable)
    }
    @Test fun writeFailsHalfway_restoresTheBackup() {
        val a = zip(Item("notes.md", "old".toByteArray()), Item("photo.bin", other))
        val bytes = a.readBytes()
        io.failAfterBytes = 300
        assertThat(update(a, record(a, "notes.md", "new"))).isEqualTo(UpdateResult.Failed)
        assertThat(a.readBytes()).isEqualTo(bytes)
        assertThat(File(files, "update").listFiles().orEmpty()).isEmpty()
    }
    @Test fun restoreThatFails_keepsTheBackup() {
        val a = zip(Item("notes.md", "old".toByteArray()))
        io.failAfterBytes = 10
        val r = record(a, "notes.md", "new")
        io.onWrite = { io.alwaysFail = true }
        val result = update(a, r) as UpdateResult.RestoreFailed
        assertThat(File(result.backupPath).exists()).isTrue()
    }
    @Test fun cancelledWhileReplacing_finishesTheReplace() = runBlocking<Unit> {
        val a = zip(Item("notes.md", "old".toByteArray()), Item("photo.bin", other))
        val r = record(a, "notes.md", "new")
        lateinit var job: kotlinx.coroutines.Job
        io.onWrite = { job.cancel() }
        job = launch(Dispatchers.IO) { updater.update("file:${a.path}", listOf(r), password.copyOf()) }
        job.join()
        assertThat(content(a, "notes.md")).isEqualTo("new")
    }
    @Test fun zipWithAnLzmaEntry_isRefusedUntouched() {
        val a = zip(Item("notes.md", "old".toByteArray()), Item("x.bin", other, CompressionMethod.STORE))
        patchCdMethod(a, index = 1, method = LZMA)
        val bytes = a.readBytes()
        assertThat(update(a, record(a, "notes.md", "new"))).isEqualTo(UpdateResult.Unsupported)
        assertThat(a.readBytes()).isEqualTo(bytes)
    }
    @Test fun nonEfsUtf8Name_isFound() {
        val a = zip(Item("日本.txt", "old".toByteArray()))
        clearEfs(a)
        assertThat(update(a, record(a, "日本.txt", "new"))).isInstanceOf(UpdateResult.Done::class.java)
        val entry = cd(a).entries.single()
        assertThat(String(entry.name.bytes, Charsets.UTF_8)).isEqualTo("日本.txt")   // never re-encoded as CP437
        assertThat(ZipFile(a, password).use { z -> z.getInputStream(z.fileHeaders.single()).use { String(it.readBytes()) } }).isEqualTo("new")
    }
    @Test fun duplicateNames_areRefused() {
        val a = zip(Item("dup.md", "1".toByteArray()), Item("dup.md", "2".toByteArray()))
        assertThat(update(a, record(a, "dup.md", "new"))).isEqualTo(UpdateResult.Failed)
    }

    /** Sets the method of central directory record [index] (the local header is left: only the preflight reads it). */
    private fun patchCdMethod(file: File, index: Int, method: Int) {
        val e = cd(file).entries[index]
        val bytes = file.readBytes()
        val at = cdRecordOffsets(bytes)[index]
        bytes[at + 10] = method.toByte(); bytes[at + 11] = 0
        file.writeBytes(bytes)
        assertThat(cd(file).entries[index].method).isEqualTo(method).also { e.method }
    }

    /** Clears the UTF-8 flag (bit 11) in every header, keeping the UTF-8 bytes of the names. */
    private fun clearEfs(file: File) {
        val bytes = file.readBytes()
        var i = 0
        while (i < bytes.size - 4) {
            val sig = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8) or ((bytes[i + 2].toInt() and 0xFF) shl 16) or ((bytes[i + 3].toInt() and 0xFF) shl 24)
            when (sig) {
                0x04034b50 -> bytes[i + 7] = (bytes[i + 7].toInt() and 0xF7).toByte()
                0x02014b50 -> bytes[i + 9] = (bytes[i + 9].toInt() and 0xF7).toByte()
            }
            i++
        }
        file.writeBytes(bytes)
    }

    private fun cdRecordOffsets(bytes: ByteArray): List<Int> = (0 until bytes.size - 4).filter {
        bytes[it] == 0x50.toByte() && bytes[it + 1] == 0x4b.toByte() && bytes[it + 2] == 0x01.toByte() && bytes[it + 3] == 0x02.toByte()
    }

    private companion object { const val LZMA = 14 }
}
