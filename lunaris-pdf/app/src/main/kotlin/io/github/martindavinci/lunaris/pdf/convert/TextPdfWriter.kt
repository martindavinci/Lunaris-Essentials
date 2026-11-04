package io.github.martindavinci.lunaris.pdf.convert

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.text.HtmlCompat
import java.io.File
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

enum class PageFormat(val width: Int, val height: Int) { A4(595, 842), LETTER(612, 792) }

enum class PageMargins(val points: Int) { NARROW(36), NORMAL(72), WIDE(108) }

/** How text files are laid out. */
data class TextLayout(
    val format: PageFormat = PageFormat.A4,
    val landscape: Boolean = false,
    val margins: PageMargins = PageMargins.NORMAL,
) {
    val pageWidth: Int get() = if (landscape) format.height else format.width
    val pageHeight: Int get() = if (landscape) format.width else format.height
}

/** Text to pages of real text, which can be selected and searched. Only Android's own public PDF writer. */
object TextPdfWriter {
    private const val BODY_SIZE = 12f
    private const val MAX_LAYOUT_CHARS = 20_000

    suspend fun write(content: CharSequence, layout: TextLayout, file: File): Int {
        val document = PdfDocument()
        try {
            val pages = Pages(document, layout)
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = BODY_SIZE
            }
            val width = layout.pageWidth - layout.margins.points * 2
            for ((start, end) in TextPaging.chunks(content, MAX_LAYOUT_CHARS)) {
                coroutineContext.ensureActive()
                val piece = StaticLayout.Builder.obtain(content, start, end, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                pages.draw(piece)
            }
            pages.finish()
            file.outputStream().use(document::writeTo)
            return pages.count
        } finally {
            document.close()
        }
    }

    fun fromPlainText(text: String): CharSequence = SpannableString(text)

    fun fromHtml(html: String): CharSequence = HtmlCompat.fromHtml(HtmlClean.clean(html), HtmlCompat.FROM_HTML_MODE_COMPACT)

    private class Pages(private val document: PdfDocument, private val layout: TextLayout) {
        private val margin = layout.margins.points.toFloat()
        private val usable = (layout.pageHeight - layout.margins.points * 2).toFloat()
        private var page: PdfDocument.Page? = null
        private var used = 0f
        var count = 0
            private set

        fun draw(piece: StaticLayout) {
            val heights = List(piece.lineCount) { (piece.getLineBottom(it) - piece.getLineTop(it)).toFloat() }
            val spaceLeft = if (page == null) 0f else usable - used
            TextPaging.place(heights, spaceLeft, usable).forEach { part ->
                if (part.startsNewPage) newPage()
                val top = piece.getLineTop(part.lines.first)
                val bottom = piece.getLineBottom(part.lines.last)
                val canvas = checkNotNull(page).canvas
                canvas.save()
                canvas.translate(margin, margin + used - top)
                canvas.clipRect(0, top, piece.width, bottom)
                piece.draw(canvas)
                canvas.restore()
                used += bottom - top
            }
        }

        fun finish() {
            if (page == null && count == 0) newPage()
            page?.let(document::finishPage)
            page = null
        }

        private fun newPage() {
            page?.let(document::finishPage)
            count++
            page = document.startPage(PdfDocument.PageInfo.Builder(layout.pageWidth, layout.pageHeight, count).create())
            used = 0f
        }
    }
}
