package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.archive.ZipCdEntry
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod

/**
 * The zip4j part of an update, on a work copy: each changed entry is found by its stored name bytes, its old
 * content is read once (which checks the password), it is removed and added again with the same method and
 * encryption, and the result is read back. Other entries are copied as they are, never recompressed.
 */
internal object ZipRewrite {
    enum class Result { OK, NEEDS_PASSWORD, WRONG_PASSWORD, UNSUPPORTED, FAILED }

    fun rewrite(work: File, records: List<EditRecord>, password: CharArray?): Result {
        val before = directory(work) ?: return Result.FAILED
        if (!before.entries.all(::writable)) return Result.UNSUPPORTED
        val targets = records.map { r -> before.entries.filter { hex(it.name.bytes) == r.entryRaw }.singleOrNull() ?: return Result.FAILED }
        val secret = password?.copyOf()
        try {
            val changed = ZipFile(work, secret).use { zip ->
                val headers = zip.fileHeaders
                if (headers.size != before.entries.size) return Result.FAILED
                val chosen = targets.map { headers[it.order] }
                if (chosen.any { h -> headers.count { it.fileName == h.fileName } != 1 }) return Result.FAILED
                chosen.forEach { h -> check(zip, h, secret)?.let { return it } }
                val params = chosen.zip(records) { h, r -> parameters(h, r) }
                zip.removeFiles(chosen.map { it.fileName })
                records.zip(params) { r, p ->
                    val snapshot = File(checkNotNull(r.pending).path)
                    snapshot.setLastModified(r.pending.mtime)
                    zip.addFile(snapshot, p)
                }
                params.map { it.fileNameInZip }
            }
            return if (verified(work, before, targets, records, changed, secret)) Result.OK else Result.FAILED
        } catch (_: ZipException) {
            return Result.FAILED
        } catch (_: IOException) {
            return Result.FAILED
        } finally {
            secret?.fill(WIPED)
        }
    }

    /** Store or Deflate, plain or AES over them: what zip4j writes back faithfully. */
    private fun writable(e: ZipCdEntry): Boolean = when (e.method) {
        STORE, DEFLATE -> true
        AES_MARKER -> e.aes?.actualMethod in setOf(STORE, DEFLATE)
        else -> false
    }

    /** Reads the old entry whole: a wrong password shows here, before anything is written. */
    private fun check(zip: ZipFile, header: FileHeader, password: CharArray?): Result? {
        if (!header.isEncrypted) return null
        if (password == null) return Result.NEEDS_PASSWORD
        return try {
            zip.getInputStream(header).use { input -> while (input.read(ByteArray(BUFFER)) >= 0) Unit }
            null
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD || e.type == ZipException.Type.CHECKSUM_MISMATCH) Result.WRONG_PASSWORD else Result.FAILED
        } catch (_: IOException) {
            Result.WRONG_PASSWORD   // a ZipCrypto entry read with the wrong key ends in garbage that does not inflate
        }
    }

    private fun parameters(h: FileHeader, r: EditRecord) = ZipParameters().apply {
        fileNameInZip = h.fileName
        lastModifiedFileTime = checkNotNull(r.pending).mtime
        h.fileComment?.let { fileComment = it }
        val aes = h.aesExtraDataRecord
        compressionMethod = if (h.encryptionMethod == EncryptionMethod.AES && aes != null) aes.compressionMethod else h.compressionMethod
        if (h.isEncrypted) {
            isEncryptFiles = true
            encryptionMethod = h.encryptionMethod
            if (aes != null) {
                aesKeyStrength = aes.aesKeyStrength
                aesVersion = aes.aesVersion
            }
        }
    }

    /** Same number of entries; changed ones read back as their snapshots; the others with their CRC and size. */
    private fun verified(
        work: File, before: ZipDirectory, targets: List<ZipCdEntry>, records: List<EditRecord>, names: List<String>, password: CharArray?,
    ): Boolean {
        val after = directory(work) ?: return false
        if (after.entries.size != before.entries.size) return false
        val untouched = before.entries - targets.toSet()
        val byName = after.entries.groupBy { hex(it.name.bytes) }
        if (untouched.any { e -> byName[hex(e.name.bytes)]?.singleOrNull()?.let { it.crc == e.crc && it.packedSize == e.packedSize } != true }) return false
        return ZipFile(work, password).use { zip ->
            names.zip(records).all { (name, r) ->
                val header = zip.getFileHeader(name) ?: return@all false
                zip.getInputStream(header).use { Fingerprint.sha256(it) } == checkNotNull(r.pending).sha256
            }
        }
    }

    private fun directory(file: File): ZipDirectory? =
        runCatching { RandomAccessFile(file, "r").use { ZipDirectory.read(it.channel) } }.getOrNull()

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private const val STORE = 0
    private const val DEFLATE = 8
    private const val AES_MARKER = 99
    private const val BUFFER = 64 * 1024
    private val WIPED = Char(0)
}
