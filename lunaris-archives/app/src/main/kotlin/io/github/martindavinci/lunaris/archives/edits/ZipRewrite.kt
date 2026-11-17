package io.github.martindavinci.lunaris.archives.edits

import io.github.martindavinci.lunaris.archives.archive.EntryNames
import io.github.martindavinci.lunaris.archives.archive.ZipCdEntry
import io.github.martindavinci.lunaris.archives.archive.ZipDirectory
import io.github.martindavinci.lunaris.archives.extract.FreeSpace
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
    enum class Result { OK, NEEDS_PASSWORD, WRONG_PASSWORD, UNSUPPORTED, NO_SPACE, FAILED }

    /** One entry to write: where it is in the old directory, its name as text, and the snapshot that replaces it. */
    private class Change(val entry: ZipCdEntry, val name: String, val record: EditRecord)

    fun rewrite(work: File, records: List<EditRecord>, password: CharArray?): Result {
        val before = directory(work) ?: return Result.FAILED
        if (!before.entries.all(::writable)) return Result.UNSUPPORTED
        val changes = records.map { r ->
            val raw = unhex(r.entryRaw)
            val entry = before.entries.filter { it.name.bytes.contentEquals(raw) }.singleOrNull() ?: return Result.FAILED
            // The text the browser shows: zip4j writes it back as UTF-8 with the flag, never a CP437 reading of it.
            Change(entry, EntryNames.decode(raw, entry.efs, zipWithoutEfs = true), r)
        }
        val secret = password?.copyOf()
        try {
            ZipFile(work, secret).use { zip ->
                val headers = zip.fileHeaders
                if (headers.size != before.entries.size) return Result.FAILED
                val chosen = changes.map { headers[it.entry.order] }
                if (chosen.any { h -> headers.count { it.fileName == h.fileName } != 1 }) return Result.FAILED
                val buffer = ByteArray(BUFFER)
                chosen.forEach { h -> check(zip, h, secret, buffer)?.let { return it } }
                val params = chosen.zip(changes) { h, c -> parameters(h, c) }
                zip.removeFiles(chosen.map { it.fileName })
                changes.zip(params) { c, p ->
                    val snapshot = File(checkNotNull(c.record.pending).path)
                    snapshot.setLastModified(c.record.pending.mtime)
                    zip.addFile(snapshot, p)
                }
            }
            return if (verified(work, before, changes, secret)) Result.OK else Result.FAILED
        } catch (e: ZipException) {
            return if (FreeSpace.isNoSpace(e) || (e.cause as? IOException)?.let(FreeSpace::isNoSpace) == true) Result.NO_SPACE else Result.FAILED
        } catch (e: IOException) {
            return if (FreeSpace.isNoSpace(e)) Result.NO_SPACE else Result.FAILED
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
    private fun check(zip: ZipFile, header: FileHeader, password: CharArray?, buffer: ByteArray): Result? {
        if (!header.isEncrypted) return null
        if (password == null) return Result.NEEDS_PASSWORD
        return try {
            zip.getInputStream(header).use { input -> while (input.read(buffer) >= 0) Unit }
            null
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD || e.type == ZipException.Type.CHECKSUM_MISMATCH) Result.WRONG_PASSWORD else Result.FAILED
        } catch (_: IOException) {
            Result.WRONG_PASSWORD   // a ZipCrypto entry read with the wrong key ends in garbage that does not inflate
        }
    }

    private fun parameters(h: FileHeader, c: Change) = ZipParameters().apply {
        fileNameInZip = c.name
        lastModifiedFileTime = checkNotNull(c.record.pending).mtime
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
    private fun verified(work: File, before: ZipDirectory, changes: List<Change>, password: CharArray?): Boolean {
        val after = directory(work) ?: return false
        if (after.entries.size != before.entries.size) return false
        val touched = changes.map { it.entry }.toSet()
        val byName = after.entries.groupBy { it.name.bytes.asList() }
        val untouchedKept = (before.entries - touched).all { e ->
            byName[e.name.bytes.asList()]?.singleOrNull()?.let { it.crc == e.crc && it.packedSize == e.packedSize } == true
        }
        if (!untouchedKept) return false
        return ZipFile(work, password).use { zip ->
            changes.all { c ->
                val header = zip.getFileHeader(c.name) ?: return@all false
                zip.getInputStream(header).use { Fingerprint.sha256(it) } == checkNotNull(c.record.pending).sha256
            }
        }
    }

    private fun directory(file: File): ZipDirectory? =
        runCatching { RandomAccessFile(file, "r").use { ZipDirectory.read(it.channel) } }.getOrNull()

    private fun unhex(text: String): ByteArray = ByteArray(text.length / 2) { i -> text.substring(i * 2, i * 2 + 2).toInt(HEX).toByte() }

    private const val STORE = 0
    private const val DEFLATE = 8
    private const val AES_MARKER = 99
    private const val BUFFER = 64 * 1024
    private const val HEX = 16
    private val WIPED = Char(0)
}
