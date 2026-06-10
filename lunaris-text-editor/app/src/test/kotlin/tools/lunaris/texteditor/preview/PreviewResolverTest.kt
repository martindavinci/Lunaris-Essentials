package tools.lunaris.texteditor.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewResolverTest {
    private val renderMd: (String) -> String = { "<h1>MD</h1>" }

    @Test fun markdown_isRenderedAndWrapped() {
        val doc = PreviewResolver.toHtmlDocument("notes.md", "# x", renderMd)
        assertThat(doc).contains("<!DOCTYPE html>")
        assertThat(doc).contains("<h1>MD</h1>")
    }
    @Test fun html_isPassedThroughRaw() {
        val raw = "<html><body><p>hi</p></body></html>"
        assertThat(PreviewResolver.toHtmlDocument("page.html", raw, renderMd)).isEqualTo(raw)
    }
    @Test fun plainText_isEscapedInPre() {
        val doc = PreviewResolver.toHtmlDocument("a.txt", "1 < 2 & 3", renderMd)
        assertThat(doc).contains("<pre>1 &lt; 2 &amp; 3</pre>")
        assertThat(doc).doesNotContain("<h1>MD</h1>")
    }
    @Test fun nullFileName_treatedAsPlain() {
        assertThat(PreviewResolver.toHtmlDocument(null, "x", renderMd)).contains("<pre>x</pre>")
    }
    @Test fun extensionCaseInsensitive() {
        assertThat(PreviewResolver.toHtmlDocument("R.MD", "# x", renderMd)).contains("<h1>MD</h1>")
    }
}
