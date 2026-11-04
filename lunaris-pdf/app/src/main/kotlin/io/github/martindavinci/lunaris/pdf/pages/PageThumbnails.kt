package io.github.martindavinci.lunaris.pdf.pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PdfOpening {
    data class Pages(val count: Int) : PdfOpening
    data object Protected : PdfOpening
    data object Damaged : PdfOpening
}

data class PageSize(val width: Float, val height: Float)

/** Pictures of pages, without a viewer page. */
interface PagePictures {
    suspend fun open(uri: String): PdfOpening
    suspend fun page(uri: String, index: Int): ImageBitmap?
    suspend fun size(uri: String, index: Int): PageSize?
    suspend fun picture(uri: String): ImageBitmap?
    fun close()
}

/**
 * PdfRenderer draws one page at a time per file, so all of it stays on one thread. A provider whose file
 * cannot be read at will is copied to the cache first: PdfRenderer needs to move around in it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PageThumbnails @Inject constructor(@ApplicationContext private val context: Context) : PagePictures {
    private val thread = Dispatchers.IO.limitedParallelism(1)
    private val closing = CoroutineScope(thread)
    private val folder = File(context.cacheDir, COPY_FOLDER)
    private val renderers = HashMap<String, Opened>()
    private val pictures = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * BYTES_PER_PIXEL
    }

    override suspend fun open(uri: String): PdfOpening = withContext(thread) {
        if (looksProtected(uri)) return@withContext PdfOpening.Protected
        try {
            PdfOpening.Pages(renderer(uri).renderer.pageCount)
        } catch (_: SecurityException) {
            PdfOpening.Protected
        } catch (_: IOException) {
            PdfOpening.Damaged
        } catch (_: IllegalArgumentException) {
            PdfOpening.Damaged
        }
    }

    override suspend fun page(uri: String, index: Int): ImageBitmap? {
        val key = "$uri#$index"
        pictures.get(key)?.let { return it }
        return withContext(thread) {
            try {
                renderer(uri).renderer.openPage(index).use { page ->
                    val width = THUMB_WIDTH
                    val height = (width * page.height.toFloat() / page.width).toInt().coerceIn(1, MAX_THUMB_HEIGHT)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, Matrix().apply { setScale(width.toFloat() / page.width, height.toFloat() / page.height) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap().also { pictures.put(key, it) }
                }
            } catch (_: RuntimeException) {
                null
            } catch (_: IOException) {
                null
            }
        }
    }

    override suspend fun size(uri: String, index: Int): PageSize? = withContext(thread) {
        try {
            renderer(uri).renderer.openPage(index).use { PageSize(it.width.toFloat(), it.height.toFloat()) }
        } catch (_: RuntimeException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    /** An image that is to become a page, small enough for a thumbnail. */
    override suspend fun picture(uri: String): ImageBitmap? = pictures.get(uri) ?: withContext(thread) {
        try {
            val parsed = uri.toUri()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?.asImageBitmap()?.also { pictures.put(uri, it) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** On the same thread as the drawing: a renderer closed halfway through a page takes the app down. */
    override fun close() {
        closing.launch {
            renderers.values.forEach { it.close() }
            renderers.clear()
            pictures.evictAll()
            folder.deleteRecursively()
        }
    }

    private fun renderer(uri: String): Opened = renderers.getOrPut(uri) { open(uri, seekable(uri)) }

    private fun open(uri: String, seekable: Boolean): Opened {
        if (seekable) {
            val descriptor = readable { context.contentResolver.openFileDescriptor(uri.toUri(), READ) }
            return Opened(descriptor, PdfRenderer(descriptor), null)
        }
        folder.mkdirs()
        val copy = File(folder, UUID.randomUUID().toString())
        val input = readable { context.contentResolver.openInputStream(uri.toUri()) }
        input.use { from -> copy.outputStream().use { from.copyTo(it) } }
        val descriptor = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        return Opened(descriptor, PdfRenderer(descriptor), copy)
    }

    private fun seekable(uri: String): Boolean = try {
        readable { context.contentResolver.openFileDescriptor(uri.toUri(), READ) }.use { descriptor ->
            android.system.Os.lseek(descriptor.fileDescriptor, 0L, android.system.OsConstants.SEEK_CUR) >= 0
        }
    } catch (_: android.system.ErrnoException) {
        false
    }

    /** A lost permission is a file that cannot be read. Only PdfRenderer's own SecurityException means a password. */
    private fun <T : Any> readable(open: () -> T?): T = try {
        open() ?: throw IOException("no answer from the provider")
    } catch (failure: SecurityException) {
        throw IOException(failure)
    }

    /** PdfRenderer opens a file with an owner password all the same, but pdf-lib would refuse it after all the work. */
    private fun looksProtected(uri: String): Boolean = try {
        context.contentResolver.openFileDescriptor(uri.toUri(), READ)?.use { descriptor ->
            val size = descriptor.statSize
            java.io.FileInputStream(descriptor.fileDescriptor).use { stream ->
                val head = ByteArray(minOf(SCAN_BYTES.toLong(), size.coerceAtLeast(0)).toInt())
                val readHead = stream.read(head).coerceAtLeast(0)
                val tailStart = (size - SCAN_BYTES).coerceAtLeast(0)
                stream.channel.position(tailStart)
                val tail = ByteArray(minOf(SCAN_BYTES.toLong(), size - tailStart).toInt())
                val readTail = stream.read(tail).coerceAtLeast(0)
                PdfProtection.mentions(head.copyOf(readHead)) || PdfProtection.mentions(tail.copyOf(readTail))
            }
        } ?: false
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private class Opened(val descriptor: ParcelFileDescriptor, val renderer: PdfRenderer, val copy: File?) : Closeable {
        override fun close() {
            renderer.close()
            descriptor.close()
            copy?.delete()
        }
    }

    private companion object {
        const val READ = "r"
        const val COPY_FOLDER = "page-sources"
        const val THUMB_WIDTH = 300
        const val MAX_THUMB_HEIGHT = 1200
        const val CACHE_BYTES = 48 * 1024 * 1024
        const val BYTES_PER_PIXEL = 4
        const val SCAN_BYTES = 64 * 1024
    }
}

/** A file's trailer sits at one end or the other, never compressed, and names its encryption. */
object PdfProtection {
    private val MARK = "/Encrypt".toByteArray()

    fun mentions(bytes: ByteArray): Boolean {
        for (start in 0..bytes.size - MARK.size) {
            if (MARK.indices.all { bytes[start + it] == MARK[it] }) return true
        }
        return false
    }
}
