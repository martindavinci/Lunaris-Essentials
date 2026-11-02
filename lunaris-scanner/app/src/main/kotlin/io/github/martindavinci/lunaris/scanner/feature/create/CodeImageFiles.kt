package io.github.martindavinci.lunaris.scanner.feature.create

import android.content.Context
import android.graphics.Bitmap
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject

/** Writes code images as PNG files, with no storage permission. */
interface CodeImageFiles {
    /** Writes to a document the user picked. False when that fails. */
    suspend fun savePng(uri: String, image: CodeImage): Boolean

    /** Removes a document the user picked, which the picker has already created empty. */
    suspend fun discard(uri: String)

    /** Writes to the private cache and returns a content URI to share, or null when that fails. */
    suspend fun cachePngForSharing(image: CodeImage): String?
}

class ContentCodeImageFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CodeImageFiles {

    override suspend fun savePng(uri: String, image: CodeImage): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openOutputStream(uri.toUri(), WRITE_AND_TRUNCATE)
            stream?.use { writePng(image, it) } ?: false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    override suspend fun discard(uri: String) = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri.toUri())
            Unit
        } catch (_: IOException) {
            // Nothing more to try: the message about the failed save is already on its way.
        } catch (_: RuntimeException) {
            // Likewise: a document provider may refuse in many ways.
        }
    }

    override suspend fun cachePngForSharing(image: CodeImage): String? = withContext(Dispatchers.IO) {
        try {
            // Only the latest shared code stays in the cache, and each one gets a URI of its own: an
            // app that still holds the grant to an earlier image cannot read this one.
            val shareDir = File(context.cacheDir, SHARE_DIR).apply { deleteRecursively() }
            val file = File(File(shareDir, UUID.randomUUID().toString()).apply { mkdirs() }, SHARE_FILE)
            val written = file.outputStream().use { writePng(image, it) }
            if (written) contentUriOf(file) else null
        } catch (_: IOException) {
            null
        }
    }

    private fun contentUriOf(file: File): String =
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file).toString()

    private fun writePng(image: CodeImage, out: OutputStream): Boolean =
        image.toBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)

    private companion object {
        const val WRITE_AND_TRUNCATE = "wt"
        const val SHARE_DIR = "share"
        const val SHARE_FILE = "lunaris-code.png"
        const val AUTHORITY_SUFFIX = ".fileprovider"

        /** PNG is lossless and ignores this, but the API requires a value. */
        const val PNG_QUALITY = 100
    }
}

internal fun CodeImage.toBitmap(): Bitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
