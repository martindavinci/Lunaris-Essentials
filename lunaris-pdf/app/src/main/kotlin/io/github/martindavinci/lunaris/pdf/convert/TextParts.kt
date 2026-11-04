package io.github.martindavinci.lunaris.pdf.convert

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

enum class FileKind { PDF, IMAGE, TEXT, MARKDOWN, HTML, UNSUPPORTED }

/** What a file is, from what its provider says and from its name, which is often the only one to know. */
object CreateKinds {
    const val TEXT_BYTES_PER_PAGE = 3_000L
    const val HTML_BYTES_PER_PAGE = 6_000L
    private val IMAGES = setOf("image/jpeg", "image/png", "image/webp")
    private val BY_EXTENSION = mapOf(
        "pdf" to FileKind.PDF,
        "jpg" to FileKind.IMAGE,
        "jpeg" to FileKind.IMAGE,
        "png" to FileKind.IMAGE,
        "webp" to FileKind.IMAGE,
        "txt" to FileKind.TEXT,
        "md" to FileKind.MARKDOWN,
        "markdown" to FileKind.MARKDOWN,
        "html" to FileKind.HTML,
        "htm" to FileKind.HTML,
    )

    fun of(mime: String?, name: String): FileKind {
        BY_EXTENSION[name.substringAfterLast('.', "").lowercase()]?.let { return it }
        return when {
            mime == "application/pdf" -> FileKind.PDF
            mime in IMAGES -> FileKind.IMAGE
            mime == "text/html" -> FileKind.HTML
            mime == "text/markdown" -> FileKind.MARKDOWN
            mime == "text/plain" -> FileKind.TEXT
            else -> FileKind.UNSUPPORTED
        }
    }

    fun estimatePages(kind: FileKind, bytes: Long): Int {
        val perPage = if (kind == FileKind.HTML) HTML_BYTES_PER_PAGE else TEXT_BYTES_PER_PAGE
        return ((bytes + perPage - 1) / perPage).toInt().coerceAtLeast(1)
    }
}

/** A text file in whatever it was written. Old files on Western phones and PCs are windows-1252. */
object TextDecoding {
    private val FALLBACK = charset("windows-1252")

    fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, UTF8_MARK, bytes.size - UTF8_MARK, Charsets.UTF_8)
        bytes.startsWith(0xFF, 0xFE) -> String(bytes, UTF16_MARK, bytes.size - UTF16_MARK, Charsets.UTF_16LE)
        bytes.startsWith(0xFE, 0xFF) -> String(bytes, UTF16_MARK, bytes.size - UTF16_MARK, Charsets.UTF_16BE)
        else -> strictly(bytes, Charsets.UTF_8) ?: String(bytes, FALLBACK)
    }

    private fun strictly(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun ByteArray.startsWith(vararg start: Int): Boolean =
        size >= start.size && start.indices.all { this[it] == start[it].toByte() }

    private const val UTF8_MARK = 3
    private const val UTF16_MARK = 2
}

/** All of a stream, or null if it is longer than [limit]. The platform's own call for this needs Android 13. */
object BoundedRead {
    private const val BUFFER = 64 * 1024

    fun of(stream: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) return out.toByteArray()
            out.write(buffer, 0, count)
            if (out.size() > limit) return null
        }
    }
}

object MarkdownHtml {
    /** Html written inside the file stays text: it is the file's words, not instructions to this app. */
    fun render(text: String): String {
        val document = Parser.builder().build().parse(text)
        return HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build().render(document)
    }
}

/**
 * Takes out of a page whatever is not words, before Android's reader of html sees it: that one would print
 * the text of a script. No script runs and nothing is fetched here in any case.
 */
object HtmlClean {
    private const val OPTIONS = "(?is)"
    private val COMMENT = Regex("$OPTIONS<!--.*?(-->|$)")
    private val HOLDERS = Regex("$OPTIONS<(script|style|head|iframe|object|svg|noscript|template|audio|video|canvas)\\b[^>]*>.*?(</\\1\\s*>|$)")
    private val EMPTY_TAGS = Regex("$OPTIONS<(embed|link|meta|base)\\b[^>]*>")
    private val IMAGE = Regex("$OPTIONS<img\\b[^>]*>")
    private val ALT = Regex("$OPTIONS\\balt\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
    private val PRE = Regex("$OPTIONS<pre\\b[^>]*>(.*?)(</pre\\s*>|$)")
    private val CODE_TAG = Regex("$OPTIONS</?code\\b[^>]*>")
    private val SHORT_CODE = Regex("$OPTIONS<code\\b[^>]*>(.*?)</code\\s*>")

    fun clean(html: String): String {
        var out = COMMENT.replace(html, "")
        out = HOLDERS.replace(out, "")
        out = EMPTY_TAGS.replace(out, "")
        out = IMAGE.replace(out) { tag ->
            val alt = ALT.find(tag.value)
            alt?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }.orEmpty()
        }
        out = PRE.replace(out) { block ->
            val body = CODE_TAG.replace(block.groupValues[1], "").trim('\n').replace(" ", "&nbsp;").replace("\n", "<br>")
            "<p><tt>$body</tt></p>"
        }
        return SHORT_CODE.replace(out) { "<tt>${it.groupValues[1]}</tt>" }
    }
}

/** The lines that go together on a page. */
data class PagePart(val lines: IntRange, val startsNewPage: Boolean)

/** How text flows over pages. A line is never cut, and none is left out. */
object TextPaging {
    fun place(heights: List<Float>, spaceLeft: Float, pageHeight: Float): List<PagePart> {
        val parts = mutableListOf<PagePart>()
        var left = spaceLeft
        var first = 0
        var startsNew = false
        heights.forEachIndexed { line, height ->
            if (height > left && (line > first || left < pageHeight)) {
                if (line > first) {
                    parts += PagePart(first..line - 1, startsNew)
                    first = line
                }
                startsNew = true
                left = pageHeight
            }
            left -= height
        }
        if (first < heights.size) parts += PagePart(first..heights.lastIndex, startsNew)
        return parts
    }

    /** Pieces of a long text, so that no single layout has to hold it all. Each ends at a line's end if it can. */
    fun chunks(text: CharSequence, maxChars: Int): List<Pair<Int, Int>> {
        val pieces = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (start < text.length) {
            val limit = minOf(start + maxChars, text.length)
            val end = if (limit == text.length) limit else lastBreak(text, start, limit)
            pieces += start to end
            start = end
        }
        return pieces
    }

    private fun lastBreak(text: CharSequence, start: Int, limit: Int): Int {
        for (at in limit downTo start + 1) {
            if (text[at - 1] == '\n') return at
        }
        for (at in limit downTo start + 1) {
            if (text[at - 1] == ' ') return at
        }
        return limit
    }
}
