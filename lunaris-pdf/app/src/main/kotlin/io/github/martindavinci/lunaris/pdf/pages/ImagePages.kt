package io.github.martindavinci.lunaris.pdf.pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An image ready to become a page: [uri] holds it as a JPEG or a PNG, [turn] is how it should stand. */
data class PreparedImage(val uri: String, val kind: SourceKind, val turn: Int)

fun interface ImagePages {
    /** Null if it cannot be read as an image. */
    suspend fun prepare(uri: String): PreparedImage?
}

/** How EXIF says an image should stand. A quarter turn fits the page's rotation; a mirror does not. */
data class ExifTurn(val turn: Int, val mirrored: Boolean) {
    companion object {
        private const val QUARTER = 90
        private const val HALF = 180
        private const val THREE_QUARTERS = 270

        fun of(orientation: Int): ExifTurn = when (orientation) {
            ExifValues.MIRROR_HORIZONTAL -> ExifTurn(0, true)
            ExifValues.ROTATE_180 -> ExifTurn(HALF, false)
            ExifValues.MIRROR_VERTICAL -> ExifTurn(HALF, true)
            ExifValues.TRANSPOSE -> ExifTurn(QUARTER, true)
            ExifValues.ROTATE_90 -> ExifTurn(QUARTER, false)
            ExifValues.TRANSVERSE -> ExifTurn(THREE_QUARTERS, true)
            ExifValues.ROTATE_270 -> ExifTurn(THREE_QUARTERS, false)
            else -> ExifTurn(0, false)
        }
    }
}

/** The numbers of the EXIF standard, here so that the logic above can be tested without Android. */
internal object ExifValues {
    const val MIRROR_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val MIRROR_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8
}

/** JPEG and PNG go as they are, with no loss. Only what a PDF cannot hold directly is made again, in the cache. */
class ContentImagePages @Inject constructor(@ApplicationContext private val context: Context) : ImagePages {

    override suspend fun prepare(uri: String): PreparedImage? = withContext(Dispatchers.IO) {
        try {
            val format = outMime(uri) ?: return@withContext null
            val exif = ExifTurn.of(orientation(uri))
            when {
                format == MIME_PNG -> PreparedImage(uri, SourceKind.PNG, exif.turn)
                format == MIME_JPEG && !exif.mirrored -> PreparedImage(uri, SourceKind.JPEG, exif.turn)
                else -> rewritten(uri, exif)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun outMime(uri: String): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, bounds) }
        return bounds.outMimeType
    }

    private fun orientation(uri: String): Int = try {
        context.contentResolver.openInputStream(uri.toUri())?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun rewritten(uri: String, exif: ExifTurn): PreparedImage? {
        val decoded = context.contentResolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it) } ?: return null
        val image = if (exif.mirrored) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { preScale(-1f, 1f) }, true)
        } else {
            decoded
        }
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val keepsAlpha = image.hasAlpha()
        val file = File(folder, UUID.randomUUID().toString())
        file.outputStream().use { output ->
            val format = if (keepsAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            if (!image.compress(format, JPEG_QUALITY, output)) return null
        }
        return PreparedImage(android.net.Uri.fromFile(file).toString(), if (keepsAlpha) SourceKind.PNG else SourceKind.JPEG, exif.turn)
    }

    private companion object {
        /** Goes with the copies of [PageThumbnails]: same life, same cleaning. */
        const val FOLDER = "page-sources"
        const val MIME_PNG = "image/png"
        const val MIME_JPEG = "image/jpeg"
        const val JPEG_QUALITY = 92
    }
}
