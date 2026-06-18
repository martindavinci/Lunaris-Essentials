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

    @Test fun gfmTable_rendersTable() {
        val md = "| A | B |\n| - | - |\n| 1 | 2 |"
        val html = renderer.toHtml(md)
        assertThat(html).contains("<table>")
        assertThat(html).contains("<th>A</th>")
        assertThat(html).contains("<td>1</td>")
    }

    @Test fun gfmTable_withoutPrecedingBlankLine_stillRenders() {
        val md = "**Header paragraph**\n| A | B |\n| - | - |\n| 1 | 2 |"
        val html = renderer.toHtml(md)
        assertThat(html).contains("<table>")
        assertThat(html).contains("<th>A</th>")
        assertThat(html).contains("<td>1</td>")
    }

    @Test fun setextHeading_dashes_notTreatedAsTable() {
        // "text\n---" is a setext H2, not a table — must stay an <h2>, no blank-line injection.
        val html = renderer.toHtml("Title\n---")
        assertThat(html).contains("<h2>Title</h2>")
        assertThat(html).doesNotContain("<table>")
    }

    @Test fun taskList_rendersCheckboxes() {
        val html = renderer.toHtml("- [ ] todo\n- [x] done")
        assertThat(html).contains("type=\"checkbox\"")
        assertThat(html).contains("checked")
    }
}
