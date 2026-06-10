package tools.lunaris.texteditor.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownRendererTest {
    private val renderer = MarkdownRenderer()

    @Test fun heading_rendersH1() { assertThat(renderer.toHtml("# Hello")).contains("<h1>Hello</h1>") }
    @Test fun bold_rendersStrong() { assertThat(renderer.toHtml("**x**")).contains("<strong>x</strong>") }
    @Test fun list_rendersUl() {
        val html = renderer.toHtml("- a\n- b")
        assertThat(html).contains("<ul>")
        assertThat(html).contains("<li>a</li>")
    }
    @Test fun empty_isEmpty() { assertThat(renderer.toHtml("")).isEmpty() }
}
