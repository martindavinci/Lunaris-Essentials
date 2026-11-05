package io.github.martindavinci.lunaris.scanner.document

import android.content.Context
import android.graphics.Bitmap
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject

enum class PictureFormat { PNG, JPEG }

/** A page of the scan under way. [thumbnail] is there once it has been rendered. */
data class DocumentPage(
    val id: Long,
    val photoPath: String,
    val workSize: PageSize,
    val settings: PageSettings,
    val thumbnail: PagePixels? = null,
)

/** Writes the finished pages, one at a time, with no storage permission. */
interface DocumentFiles {
    suspend fun savePdf(uri: String, pages: List<DocumentPage>): Boolean

    suspend fun savePicture(uri: String, page: DocumentPage, format: PictureFormat): Boolean

    /** Removes a document the user picked, which the picker has already created empty. */
    suspend fun discard(uri: String)

    /** Writes to the private cache and returns a content URI to share, or null when that fails. */
    suspend fun cachePdfForSharing(pages: List<DocumentPage>): String?
}

internal const val SHARE_DIR = "share"

private const val PAGE_LONG_SIDE = 3508
private const val JPEG_QUALITY = 88
private const val PNG_QUALITY = 100
private const val WRITE_AND_TRUNCATE = "wt"
private const val SHARE_FILE = "lunaris-scan.pdf"
private const val AUTHORITY_SUFFIX = ".fileprovider"

class ContentDocumentFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val photos: DocumentPhotos,
) : DocumentFiles {

    override suspend fun savePdf(uri: String, pages: List<DocumentPage>): Boolean = guarded {
        toDocument(uri) { writePdf(pages, it) }
    }

    override suspend fun savePicture(uri: String, page: DocumentPage, format: PictureFormat): Boolean = guarded {
        toDocument(uri) { out ->
            val bitmap = fullPage(page) ?: return@toDocument false
            val written = when (format) {
                PictureFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                PictureFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()
            written
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

    override suspend fun cachePdfForSharing(pages: List<DocumentPage>): String? = withContext(Dispatchers.IO) {
        try {
            // Each file gets a URI of its own: an app that still holds the grant to an earlier one cannot read this one.
            val shareDir = File(context.cacheDir, SHARE_DIR).apply { deleteRecursively() }
            val file = File(File(shareDir, UUID.randomUUID().toString()).apply { mkdirs() }, SHARE_FILE)
            val written = file.outputStream().use { writePdf(pages, it) }
            if (written) FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file).toString() else null
        } catch (_: IOException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private suspend fun guarded(work: suspend () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            work()
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    private suspend fun toDocument(uri: String, write: suspend (OutputStream) -> Boolean): Boolean {
        val stream = context.contentResolver.openOutputStream(uri.toUri(), WRITE_AND_TRUNCATE) ?: return false
        return stream.use { write(it) }
    }

    /** Only one page is in memory at any moment. */
    private suspend fun writePdf(pages: List<DocumentPage>, out: OutputStream): Boolean {
        val writer = ScanPdfWriter(out)
        pages.forEach { page ->
            val bitmap = fullPage(page) ?: return false
            val jpeg = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)
            val size = PageSize(bitmap.width, bitmap.height)
            bitmap.recycle()
            writer.addJpegPage(jpeg.toByteArray(), size, pagePoints(size, page.settings.paper))
        }
        writer.finish()
        return true
    }

    private suspend fun fullPage(page: DocumentPage): Bitmap? {
        val photo = photos.load(page.photoPath, WORK_LONG_SIDE) ?: return null
        val rendered = renderPage(photo, page.settings, page.workSize, PAGE_LONG_SIDE)
        return Bitmap.createBitmap(rendered.pixels, rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
    }
}
