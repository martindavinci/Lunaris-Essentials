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
    @Test fun html_darkMode_invertsColorsButKeepsRaw() {
        val raw = "<html><body><p>hi</p></body></html>"
        val doc = PreviewResolver.toHtmlDocument("page.html", raw, renderMd, dark = true)
        assertThat(doc).contains("invert(1)")
        assertThat(doc).contains(raw)
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

    @Test fun highlight_injectedWhenCodeBlockPresent() {
        val withCode: (String) -> String = { "<pre><code class=\"language-py\">x=1</code></pre>" }
        val doc = PreviewResolver.toHtmlDocument(
            "a.md", "```py", withCode, dark = true, highlightCss = "CSSDATA", highlightJs = "JSDATA",
        )
        assertThat(doc).contains("hljs.highlightAll()")
        assertThat(doc).contains("CSSDATA")
        assertThat(doc).contains("JSDATA")
    }

    @Test fun codeFile_taggedWithLanguageClass_andHighlighted() {
        val doc = PreviewResolver.toHtmlDocument(
            "main.py", "x = 1", renderMd, dark = true, highlightCss = "CSSDATA", highlightJs = "JSDATA",
        )
        assertThat(doc).contains("<code class=\"language-py\">x = 1</code>")
        assertThat(doc).contains("hljs.highlightAll()")
        assertThat(doc).doesNotContain("<h1>MD</h1>")
    }

    @Test fun codeDocument_darkMonochrome_andA4Print() {
        val doc = PreviewResolver.toHtmlDocument(
            "a.cpp", "x", renderMd, dark = true, highlightCss = "C", highlightJs = "J",
        )
        assertThat(doc).contains("background:#1e1e1e")
        assertThat(doc).contains("@page{size:A4")
        assertThat(doc).contains("hljs.highlightAll()")
    }

    @Test fun codeDocument_light_usesWhiteBackground() {
        val doc = PreviewResolver.toHtmlDocument("a.cpp", "x", renderMd, dark = false)
        assertThat(doc).contains("background:#ffffff")
        assertThat(doc).contains("@page{size:A4")
    }

    @Test fun plainTxt_notHighlighted_evenWithAssets() {
        val doc = PreviewResolver.toHtmlDocument(
            "a.txt", "x", renderMd, dark = true, highlightCss = "CSSDATA", highlightJs = "JSDATA",
        )
        assertThat(doc).contains("<pre>x</pre>")
        assertThat(doc).doesNotContain("hljs.highlightAll()")
    }

    @Test fun untitledFile_usesLanguageHint_forHighlight() {
        // No extension to derive the language from, so the explicitly-chosen syntax must drive it.
        val doc = PreviewResolver.toHtmlDocument(
            fileName = "untitled", text = "int main(){}", renderMarkdown = renderMd,
            dark = true, highlightCss = "CSSDATA", highlightJs = "JSDATA", languageHint = "cpp",
        )
        assertThat(doc).contains("<code class=\"language-cpp\">int main(){}</code>")
        assertThat(doc).contains("hljs.highlightAll()")
    }
    @Test fun realExtension_winsOverLanguageHint() {
        // A genuine extension is authoritative; the hint only fills in when there isn't one.
        val doc = PreviewResolver.toHtmlDocument(
            fileName = "main.py", text = "x = 1", renderMarkdown = renderMd,
            highlightCss = "C", highlightJs = "J", languageHint = "cpp",
        )
        assertThat(doc).contains("<code class=\"language-py\">x = 1</code>")
    }
    @Test fun isRawHtml_honorsLanguageHintWhenNoExtension() {
        assertThat(PreviewResolver.isRawHtml(fileName = "untitled", languageHint = "html")).isTrue()
        assertThat(PreviewResolver.isRawHtml(fileName = "untitled", languageHint = "cpp")).isFalse()
    }

    @Test fun highlight_notInjectedWithoutCodeBlock() {
        val doc = PreviewResolver.toHtmlDocument(
            "a.md", "# x", renderMd, dark = true, highlightCss = "CSSDATA", highlightJs = "JSDATA",
        )
        assertThat(doc).doesNotContain("hljs.highlightAll()")
        assertThat(doc).doesNotContain("JSDATA")
    }

    // --- toSpeakableText: Markdown is stripped to prose; everything else is spoken verbatim. ---
    private val strip: (String) -> String = { "STRIPPED" }

    @Test fun speakable_markdown_isStripped() {
        assertThat(PreviewResolver.toSpeakableText("notes.md", "# x", strip)).isEqualTo("STRIPPED")
    }
    @Test fun speakable_code_isRaw() {
        assertThat(PreviewResolver.toSpeakableText("main.py", "x = 1", strip)).isEqualTo("x = 1")
    }
    @Test fun speakable_plainTxt_isRaw() {
        assertThat(PreviewResolver.toSpeakableText("a.txt", "hello", strip)).isEqualTo("hello")
    }
    @Test fun speakable_untitledMarkdownHint_isStripped() {
        assertThat(PreviewResolver.toSpeakableText("untitled", "# x", strip, languageHint = "markdown"))
            .isEqualTo("STRIPPED")
    }
    @Test fun speakable_realExtension_winsOverHint() {
        assertThat(PreviewResolver.toSpeakableText("main.py", "x", strip, languageHint = "md"))
            .isEqualTo("x")
    }
}
