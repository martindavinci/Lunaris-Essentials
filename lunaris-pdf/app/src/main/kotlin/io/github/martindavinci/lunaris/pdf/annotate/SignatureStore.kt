package io.github.martindavinci.lunaris.pdf.annotate

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [path] is where the picture of the signature is. */
data class SavedSignature(val id: String, val path: String)

/** Signatures drawn by the user, kept only on this phone. */
interface SignatureLibrary {
    suspend fun list(): List<SavedSignature>
    suspend fun add(png: ByteArray)
    suspend fun remove(id: String)
    suspend fun read(id: String): ByteArray?
}

class SignatureStore(private val directory: File) : SignatureLibrary {

    override suspend fun list(): List<SavedSignature> = onDisk {
        directory.listFiles().orEmpty()
            .filter { it.name.endsWith(EXTENSION) }
            .sortedBy { it.lastModified() }
            .map { SavedSignature(it.name.removeSuffix(EXTENSION), it.path) }
    }

    /** Anything but a PNG is left out. A failed write only means the signature has to be drawn again. */
    override suspend fun add(png: ByteArray) {
        if (!png.startsWith(PNG_START)) return
        onDisk {
            try {
                directory.mkdirs()
                File(directory, UUID.randomUUID().toString() + EXTENSION).writeBytes(png)
            } catch (_: IOException) {
                Unit
            }
        }
    }

    override suspend fun remove(id: String) {
        onDisk { fileOf(id)?.delete() }
    }

    override suspend fun read(id: String): ByteArray? = onDisk {
        try {
            fileOf(id)?.takeIf { it.isFile }?.readBytes()
        } catch (_: IOException) {
            null
        }
    }

    /** Only a plain name: an id never leads out of the folder. */
    private fun fileOf(id: String): File? = id.takeIf { VALID_ID.matches(it) }?.let { File(directory, it + EXTENSION) }

    private suspend fun <T> onDisk(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun ByteArray.startsWith(start: ByteArray): Boolean =
        size >= start.size && start.indices.all { this[it] == start[it] }

    private companion object {
        const val EXTENSION = ".png"
        val VALID_ID = Regex("[A-Za-z0-9-]+")
        val PNG_START = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    }
}
