package io.github.martindavinci.lunaris.scanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.share.sampleSizeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Reads the code in the picture at a content URI, or null when there is none or it cannot be read. */
fun interface ImageDecoder {
    suspend fun decode(uri: String): ScannedCode?
}

/** Large enough to keep a printed code readable, small enough not to run out of memory. */
private const val MAX_IMAGE_SIDE_PX = 2048

/** The noise in a sharp photo can hide a code that reads fine at a smaller size. */
private const val SMALLER_IMAGE_SIDE_PX = 1024

private const val CONTENT_SCHEME = "content"
private const val USER_ID_SEPARATOR = '@'

/** The bitmap sample sizes to try, best first. None when the bounds say it is not an image. */
internal fun decodeSampleSizes(width: Int, height: Int): List<Int> =
    if (width <= 0 || height <= 0) {
        emptyList()
    } else {
        listOf(MAX_IMAGE_SIDE_PX, SMALLER_IMAGE_SIDE_PX).map { sampleSizeFor(width, height, it) }.distinct()
    }

/**
 * A shared URI comes from any app and is opened with this app's own rights, so only content that
 * belongs to another app is read: never a file path, never a provider of this app.
 */
internal fun isReadableSource(scheme: String?, authority: String?, ownPackage: String): Boolean =
    scheme == CONTENT_SCHEME && authority != null &&
        !authority.substringAfterLast(USER_ID_SEPARATOR).startsWith(ownPackage)

class ContentImageDecoder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val decoder: CodeDecoder,
) : ImageDecoder {

    override suspend fun decode(uri: String): ScannedCode? = withContext(Dispatchers.IO) {
        try {
            val parsed = uri.toUri()
            if (!isReadableSource(parsed.scheme, parsed.authority, context.packageName)) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(parsed).use { BitmapFactory.decodeStream(it, null, bounds) }
            decodeSampleSizes(bounds.outWidth, bounds.outHeight).firstNotNullOfOrNull { decodeAt(parsed, it) }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            // SecurityException, and whatever else another app's provider sends back over Binder.
            null
        } catch (_: OutOfMemoryError) {
            // The size was read from a separate stream, so the picture may be larger than it claimed.
            null
        }
    }

    private fun decodeAt(uri: Uri, sampleSize: Int): ScannedCode? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = open(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        return decodeBitmap(bitmap).also { bitmap.recycle() }
    }

    private fun decodeBitmap(bitmap: Bitmap): ScannedCode? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return decoder.decodeArgb(pixels, bitmap.width, bitmap.height)
    }

    private fun open(uri: Uri) =
        context.contentResolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
}
