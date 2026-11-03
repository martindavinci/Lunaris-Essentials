package io.github.martindavinci.lunaris.pdf.recents

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the Reader does with the picture of a first page. */
interface DocumentThumbnails {
    suspend fun has(uri: String): Boolean
    suspend fun save(uri: String, jpeg: ByteArray)
}

/**
 * The first pages shown in the recents, one small picture per document. Files are named by a hash, so
 * their names say nothing of the documents, and they leave with the entries they belong to.
 */
class ThumbnailStore(private val directory: File) : DocumentThumbnails {

    fun fileOf(uri: String): File = File(directory, hash(uri) + EXTENSION)

    override suspend fun has(uri: String): Boolean = onDisk { fileOf(uri).isFile }

    /** Anything but a JPEG is left out. A failed write only means no picture until the next try. */
    override suspend fun save(uri: String, jpeg: ByteArray) {
        if (!jpeg.startsWith(JPEG_START)) return
        onDisk {
            try {
                directory.mkdirs()
                val temporary = File(directory, hash(uri) + TEMPORARY_EXTENSION)
                temporary.writeBytes(jpeg)
                if (!temporary.renameTo(fileOf(uri))) temporary.delete()
            } catch (_: IOException) {
                Unit
            }
        }
    }

    suspend fun remove(uri: String) {
        onDisk { fileOf(uri).delete() }
    }

    suspend fun retain(uris: List<String>) {
        val kept = uris.map { fileOf(it).name }.toSet()
        onDisk { directory.listFiles()?.filterNot { it.name in kept }?.forEach { it.delete() } }
    }

    suspend fun clear() {
        onDisk { directory.deleteRecursively() }
    }

    private suspend fun <T> onDisk(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun hash(uri: String): String =
        MessageDigest.getInstance(HASH).digest(uri.toByteArray()).joinToString("") { HEX.format(it) }

    private fun ByteArray.startsWith(start: ByteArray): Boolean =
        size >= start.size && start.indices.all { this[it] == start[it] }

    private companion object {
        const val EXTENSION = ".jpg"
        const val TEMPORARY_EXTENSION = ".tmp"
        const val HASH = "SHA-256"
        const val HEX = "%02x"
        val JPEG_START = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    }
}
