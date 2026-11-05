package io.github.martindavinci.lunaris.scanner.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.martindavinci.lunaris.scanner.scan.isReadableSource
import io.github.martindavinci.lunaris.scanner.share.sampleSizeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.concurrent.thread

/** A photo copied into the app, upright and at the size pages are made from. */
data class ImportedPhoto(val path: String, val size: PageSize)

/** The photos of the scan under way. They live in the private cache and only until the scan ends. */
interface DocumentPhotos {
    /** Null if it cannot be read as an image. */
    suspend fun imported(uri: String): ImportedPhoto?

    /** Takes over a file written by the camera. Null if it cannot be read. */
    suspend fun captured(path: String): ImportedPhoto?

    /** The photo in pixels, its long side at most [longSide]. Null if that fails, for one thing when memory runs out. */
    suspend fun load(path: String, longSide: Int): PagePixels?

    fun delete(path: String)
}

private const val FOLDER = "document"
private const val PHOTO_QUALITY = 95

/** Where the camera writes, and where every photo of the scan under way is kept. */
internal fun newPhotoFile(cacheDir: File): File =
    File(File(cacheDir, FOLDER).apply { mkdirs() }, "${UUID.randomUUID()}.jpg")

/** What a scan left behind when the process ended, and the last shared file. Nothing is kept from one run to the next. */
internal fun clearPhotosLeftBehind(cacheDir: File) {
    thread {
        File(cacheDir, FOLDER).deleteRecursively()
        File(cacheDir, SHARE_DIR).deleteRecursively()
    }
}

class ContentDocumentPhotos @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DocumentPhotos {

    override suspend fun imported(uri: String): ImportedPhoto? = guarded {
        val parsed = uri.toUri()
        if (!isReadableSource(parsed.scheme, parsed.authority, context.packageName)) return@guarded null
        val open = { context.contentResolver.openInputStream(parsed) ?: throw IOException("No stream") }
        normalized(open)
    }

    override suspend fun captured(path: String): ImportedPhoto? = guarded {
        val file = File(path)
        try {
            normalized { file.inputStream() }
        } finally {
            file.delete()
        }
    }

    override suspend fun load(path: String, longSide: Int): PagePixels? = guarded {
        val bounds = boundsOf { File(path).inputStream() }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@guarded null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, longSide) }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return@guarded null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        PagePixels(pixels, bitmap.width, bitmap.height).also { bitmap.recycle() }
    }

    override fun delete(path: String) {
        File(path).delete()
    }

    private suspend fun <T> guarded(work: () -> T?): T? = withContext(Dispatchers.IO) {
        try {
            work()
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            // SecurityException, and whatever else another app's provider sends back over Binder.
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun boundsOf(open: () -> InputStream) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        open().use { BitmapFactory.decodeStream(it, null, this) }
    }

    /** No EXIF comes along: a copy made this way says nothing of where or when the photo was taken. */
    private fun normalized(open: () -> InputStream): ImportedPhoto? {
        val bounds = boundsOf(open)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = workSampleSize(bounds.outWidth, bounds.outHeight, WORK_LONG_SIDE) }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val exif = open().use { ExifTurn.of(ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) }
        val upright = upright(decoded, exif)
        val file = newPhotoFile(context.cacheDir)
        file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
        return ImportedPhoto(file.path, PageSize(upright.width, upright.height)).also { upright.recycle() }
    }

    /** At the work size, turned as it should stand, and on white: a JPEG would make anything transparent black. */
    private fun upright(decoded: Bitmap, exif: ExifTurn): Bitmap {
        val matrix = Matrix()
        if (exif.mirrored) matrix.postScale(-1f, 1f)
        matrix.postRotate(exif.turn.toFloat())
        val stood = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val size = workSize(stood.width, stood.height, WORK_LONG_SIDE)
        val scaled = Bitmap.createScaledBitmap(stood, size.width, size.height, true)
        if (!scaled.hasAlpha()) return scaled
        val flat = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        return flat
    }
}
