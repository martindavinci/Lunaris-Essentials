package io.github.martindavinci.lunaris.pdf.convert

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextDecodingTest {
    @Test
    fun `utf-8 with and without its mark`() {
        assertThat(TextDecoding.decode("caffè €".toByteArray())).isEqualTo("caffè €")
        assertThat(TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "ok".toByteArray())).isEqualTo("ok")
    }

    @Test
    fun `utf-16 by its mark`() {
        assertThat(TextDecoding.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "ok".toByteArray(Charsets.UTF_16LE))).isEqualTo("ok")
        assertThat(TextDecoding.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "ok".toByteArray(Charsets.UTF_16BE))).isEqualTo("ok")
    }

    @Test
    fun `an old Western file falls back`() {
        assertThat(TextDecoding.decode("caffè €".toByteArray(charset("windows-1252")))).isEqualTo("caffè €")
    }
}

class BoundedReadTest {
    @Test
    fun `a stream within the limit comes whole`() {
        val bytes = ByteArray(5000) { it.toByte() }
        assertThat(BoundedRead.of(bytes.inputStream(), 5000)).isEqualTo(bytes)
        assertThat(BoundedRead.of(ByteArray(0).inputStream(), 10)).isEqualTo(ByteArray(0))
    }

    @Test
    fun `a stream past the limit is refused`() {
        assertThat(BoundedRead.of(ByteArray(5001).inputStream(), 5000)).isNull()
    }

    @Test
    fun `a stream that gives little at a time still comes whole`() {
        val bytes = ByteArray(1000) { it.toByte() }
        val slow = object : java.io.ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 7))
        }
        assertThat(BoundedRead.of(slow, 2000)).isEqualTo(bytes)
    }
}

class MarkdownHtmlTest {
    @Test
    fun `the usual marks become html`() {
        val html = MarkdownHtml.render("# Title\n\nSome **bold** and *soft* text\n\n- one\n- two\n\n> said\n\n---\n")
        assertThat(html).contains("<h1>Title</h1>")
        assertThat(html).contains("<strong>bold</strong>")
        assertThat(html).contains("<em>soft</em>")
        assertThat(html).contains("<li>one</li>")
        assertThat(html).contains("<blockquote>")
    }

    @Test
    fun `html written in the file stays text`() {
        val html = MarkdownHtml.render("a <script>alert(1)</script> b")
        assertThat(html).doesNotContain("<script>")
        assertThat(html).contains("&lt;script&gt;")
    }
}

class HtmlCleanTest {
    @Test
    fun `what runs or styles is gone, with what it holds`() {
        val out = HtmlClean.clean("<head><title>t</title></head><p>a</p><script>evil()</script><style>x{}</style><!-- note --><p>b</p>")
        assertThat(out).contains("<p>a</p>")
        assertThat(out).contains("<p>b</p>")
        listOf("evil", "x{}", "note", "<title>").forEach { assertThat(out).doesNotContain(it) }
    }

    @Test
    fun `upper case and attributes do not hide it`() {
        assertThat(HtmlClean.clean("<SCRIPT type='x'>evil()</SCRIPT >ok")).isEqualTo("ok")
    }

    @Test
    fun `one left open takes the rest with it`() {
        assertThat(HtmlClean.clean("ok<script>evil()")).isEqualTo("ok")
    }

    @Test
    fun `frames and objects go too`() {
        val out = HtmlClean.clean("<iframe src='http://x'>a</iframe><object>b</object><embed src='y'><svg>c</svg>ok")
        assertThat(out).isEqualTo("ok")
    }

    @Test
    fun `an image is its description, and fetches nothing`() {
        assertThat(HtmlClean.clean("""<img src="http://x/a.png" alt="The moon">""")).isEqualTo("The moon")
        assertThat(HtmlClean.clean("""<img src="http://x/a.png">""")).isEmpty()
    }

    @Test
    fun `code keeps its look and its lines`() {
        val out = HtmlClean.clean("<pre><code>a  b\nc</code></pre>")
        assertThat(out).contains("<tt>")
        assertThat(out).contains("a&nbsp;&nbsp;b<br>c")
    }
}

class TextPagingTest {
    @Test
    fun `what fits stays on the page`() {
        val parts = TextPaging.place(listOf(10f, 10f), spaceLeft = 100f, pageHeight = 100f)
        assertThat(parts).containsExactly(PagePart(0..1, startsNewPage = false))
    }

    @Test
    fun `lines that do not fit go on, none is cut or lost`() {
        val parts = TextPaging.place(List(25) { 10f }, spaceLeft = 45f, pageHeight = 100f)
        assertThat(parts).containsExactly(
            PagePart(0..3, false),
            PagePart(4..13, true),
            PagePart(14..23, true),
            PagePart(24..24, true),
        ).inOrder()
    }

    @Test
    fun `a line taller than a page gets a page of its own`() {
        val parts = TextPaging.place(listOf(10f, 300f, 10f), spaceLeft = 100f, pageHeight = 100f)
        assertThat(parts.map { it.lines }).containsExactly(0..0, 1..1, 2..2).inOrder()
    }

    @Test
    fun `no space at all starts a page`() {
        assertThat(TextPaging.place(listOf(10f), spaceLeft = 0f, pageHeight = 100f)).containsExactly(PagePart(0..0, true))
    }

    @Test
    fun `no lines, no parts`() {
        assertThat(TextPaging.place(emptyList(), 10f, 100f)).isEmpty()
    }

    @Test
    fun `long text is cut where a paragraph or line ends`() {
        val text = "aaaa\n\nbbbb\ncccc dddd"
        assertThat(TextPaging.chunks(text, maxChars = 6)).containsExactly(0 to 6, 6 to 11, 11 to 16, 16 to 20).inOrder()
    }

    @Test
    fun `a chunk never comes out empty, even with no place to cut`() {
        assertThat(TextPaging.chunks("aaaaaaaaaa", maxChars = 4)).containsExactly(0 to 4, 4 to 8, 8 to 10).inOrder()
        assertThat(TextPaging.chunks("", maxChars = 4)).isEmpty()
    }
}

class CreateKindsTest {
    @Test
    fun `from the type, or else from the name`() {
        assertThat(CreateKinds.of("application/pdf", "a")).isEqualTo(FileKind.PDF)
        assertThat(CreateKinds.of("image/jpeg", "a")).isEqualTo(FileKind.IMAGE)
        assertThat(CreateKinds.of("image/png", "a")).isEqualTo(FileKind.IMAGE)
        assertThat(CreateKinds.of("image/webp", "a")).isEqualTo(FileKind.IMAGE)
        assertThat(CreateKinds.of("text/html", "a")).isEqualTo(FileKind.HTML)
        assertThat(CreateKinds.of("text/markdown", "a")).isEqualTo(FileKind.MARKDOWN)
    }

    @Test
    fun `a markdown file often says it is plain text, or nothing`() {
        assertThat(CreateKinds.of("text/plain", "notes.md")).isEqualTo(FileKind.MARKDOWN)
        assertThat(CreateKinds.of("application/octet-stream", "notes.MD")).isEqualTo(FileKind.MARKDOWN)
        assertThat(CreateKinds.of(null, "page.htm")).isEqualTo(FileKind.HTML)
        assertThat(CreateKinds.of(null, "scan.PDF")).isEqualTo(FileKind.PDF)
        assertThat(CreateKinds.of(null, "photo.jpeg")).isEqualTo(FileKind.IMAGE)
        assertThat(CreateKinds.of("text/plain", "notes")).isEqualTo(FileKind.TEXT)
    }

    @Test
    fun `the rest is not for here`() {
        assertThat(CreateKinds.of("application/vnd.ms-excel", "Budget.xlsx")).isEqualTo(FileKind.UNSUPPORTED)
        assertThat(CreateKinds.of(null, "thing")).isEqualTo(FileKind.UNSUPPORTED)
        assertThat(CreateKinds.of("image/gif", "a.gif")).isEqualTo(FileKind.UNSUPPORTED)
    }

    @Test
    fun `pages are estimated from the size, at least one`() {
        assertThat(CreateKinds.estimatePages(FileKind.TEXT, 0)).isEqualTo(1)
        assertThat(CreateKinds.estimatePages(FileKind.TEXT, 3 * CreateKinds.TEXT_BYTES_PER_PAGE)).isEqualTo(3)
        assertThat(CreateKinds.estimatePages(FileKind.HTML, 2 * CreateKinds.HTML_BYTES_PER_PAGE + 1)).isEqualTo(3)
    }
}
