package io.github.martindavinci.lunaris.pdf.pages

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.martindavinci.lunaris.pdf.actions.attempt
import io.github.martindavinci.lunaris.pdf.convert.BoundedRead
import io.github.martindavinci.lunaris.pdf.convert.CreateKinds
import io.github.martindavinci.lunaris.pdf.convert.FileKind
import io.github.martindavinci.lunaris.pdf.convert.MarkdownHtml
import io.github.martindavinci.lunaris.pdf.convert.TextDecoding
import io.github.martindavinci.lunaris.pdf.convert.TextLayout
import io.github.martindavinci.lunaris.pdf.convert.TextPdfWriter
import io.github.martindavinci.lunaris.pdf.feature.create.CreateSources
import io.github.martindavinci.lunaris.pdf.feature.create.FileFacts
import io.github.martindavinci.lunaris.pdf.feature.create.FileProblem
import io.github.martindavinci.lunaris.pdf.feature.work.JobPlanFailure
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file ready for the job: what it is now, how it turns, and how many pages it makes. */
data class PreparedSource(val uri: String, val kind: SourceKind, val turn: Int, val pages: Int)

class ContentCreateSources @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pictures: PageThumbnails,
    private val images: ContentImagePages,
) : CreateSources {
    private val resolver get() = context.contentResolver
    private val folder get() = File(context.cacheDir, FOLDER)

    override suspend fun inspect(uri: String): FileFacts = withContext(Dispatchers.IO) {
        val parsed = uri.toUri()
        val (name, size) = nameAndSize(parsed)
        val kind = CreateKinds.of(safeType(parsed), name)
        when (kind) {
            FileKind.UNSUPPORTED -> FileFacts(name, kind, 0, FileProblem.UNSUPPORTED)
            FileKind.PDF -> when (val opening = pictures.open(uri)) {
                is PdfOpening.Pages -> FileFacts(name, kind, opening.count)
                PdfOpening.Protected -> FileFacts(name, kind, 0, FileProblem.PROTECTED)
                PdfOpening.Damaged -> FileFacts(name, kind, 0, FileProblem.DAMAGED)
            }
            FileKind.IMAGE -> if (isImage(parsed)) FileFacts(name, kind, 1) else FileFacts(name, kind, 0, FileProblem.DAMAGED)
            else -> FileFacts(name, kind, CreateKinds.estimatePages(kind, size))
        }
    }

    override suspend fun prepare(uri: String, kind: FileKind, layout: TextLayout): PreparedSource? = when (kind) {
        FileKind.PDF -> (pictures.open(uri) as? PdfOpening.Pages)?.let { PreparedSource(uri, SourceKind.PDF, 0, it.count) }
        FileKind.IMAGE -> images.prepare(uri)?.let { PreparedSource(it.uri, it.kind, it.turn, 1) }
        FileKind.UNSUPPORTED -> null
        else -> withContext(Dispatchers.Default) { laidOut(uri, kind, layout) }
    }

    override suspend fun thumbnail(uri: String, kind: FileKind): ImageBitmap? = when (kind) {
        FileKind.PDF -> pictures.page(uri, 0)
        FileKind.IMAGE -> pictures.picture(uri)
        else -> null
    }

    override fun close() {
        pictures.close()
    }

    private suspend fun laidOut(uri: String, kind: FileKind, layout: TextLayout): PreparedSource? {
        val bytes = try {
            read(uri.toUri())
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        } ?: return null
        val text = TextDecoding.decode(bytes)
        val content = when (kind) {
            FileKind.HTML -> TextPdfWriter.fromHtml(text)
            FileKind.MARKDOWN -> TextPdfWriter.fromHtml(MarkdownHtml.render(text))
            else -> TextPdfWriter.fromPlainText(text)
        }
        folder.mkdirs()
        val file = File(folder, UUID.randomUUID().toString())
        val pages = try {
            TextPdfWriter.write(content, layout, file)
        } catch (_: IOException) {
            return null
        }
        return PreparedSource(Uri.fromFile(file).toString(), SourceKind.PDF, 0, pages)
    }

    private fun read(uri: Uri): ByteArray? {
        val input = resolver.openInputStream(uri) ?: return null
        return input.use { BoundedRead.of(it, MAX_TEXT_BYTES) } ?: throw JobPlanFailure(WorkFailure.MEMORY, null)
    }

    private fun isImage(uri: Uri): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        attempt { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        return bounds.outWidth > 0
    }

    private fun safeType(uri: Uri): String? {
        var type: String? = null
        attempt { type = resolver.getType(uri) }
        return type
    }

    private fun nameAndSize(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = 0L
        attempt {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        }
        return (name ?: uri.lastPathSegment?.substringAfterLast('/')).orEmpty() to size
    }

    private companion object {
        const val FOLDER = "page-sources"
        const val MAX_TEXT_BYTES = 32 * 1024 * 1024
    }
}
