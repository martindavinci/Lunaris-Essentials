package io.github.martindavinci.lunaris.pdf.annotate

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A saved document as it comes out of the viewer page, a piece at a time. */
interface EditedDocuments {
    /** Forgets any file from before. */
    suspend fun begin()

    /** False for a piece out of order, or one that could not be written. */
    suspend fun append(index: Int, bytes: ByteArray): Boolean

    /** A uri to read the whole file from, or null if it is not [size] bytes long. */
    suspend fun finish(size: Long): String?
}

/** One file at a time, in the app's own cache: the next save replaces it. */
class EditedFileStore(private val directory: File) : EditedDocuments {
    private var file: File? = null
    private var output: FileOutputStream? = null
    private var nextIndex = 0

    override suspend fun begin() = onDisk {
        close()
        directory.deleteRecursively()
        file = null
        nextIndex = 0
        try {
            directory.mkdirs()
            val made = File(directory, FILE_NAME)
            output = FileOutputStream(made)
            file = made
        } catch (_: IOException) {
            file = null
        }
    }

    override suspend fun append(index: Int, bytes: ByteArray): Boolean = onDisk {
        val stream = output
        if (stream == null || index != nextIndex) {
            fail()
            return@onDisk false
        }
        try {
            stream.write(bytes)
            nextIndex++
            true
        } catch (_: IOException) {
            fail()
            false
        }
    }

    override suspend fun finish(size: Long): String? = onDisk {
        close()
        val made = file?.takeIf { it.length() == size }
        made?.let { it.toURI().toString() }
    }

    private fun fail() {
        close()
        file = null
    }

    private fun close() {
        try {
            output?.close()
        } catch (_: IOException) {
            file = null
        }
        output = null
    }

    private suspend fun <T> onDisk(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val FILE_NAME = "edited.pdf"
    }
}
