package io.github.martindavinci.lunaris.pdf.viewer

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Reads the document from any provider, without holding it in memory. */
class ContentDocumentSource(private val resolver: ContentResolver, private val uri: Uri) : DocumentSource {

    // A provider that fails here may still stream the file: then it is served whole.
    private val size: Long? by lazy {
        try {
            resolver.openAssetFileDescriptor(uri, READ_ONLY)?.use { descriptor ->
                descriptor.length.takeIf { it != AssetFileDescriptor.UNKNOWN_LENGTH && it >= 0 }
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    override fun size(): Long? = size

    override fun open(start: Long): InputStream {
        val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        try {
            stream.skipFully(start)
        } catch (failure: IOException) {
            stream.close()
            throw failure
        }
        return stream
    }

    private fun InputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            when {
                skipped > 0 -> left -= skipped
                read() >= 0 -> left--
                else -> throw EOFException("the file ended $left bytes before the requested position")
            }
        }
    }

    private companion object {
        const val READ_ONLY = "r"
    }
}
