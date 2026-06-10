package tools.lunaris.texteditor.preview

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import javax.inject.Inject

/** Converts Markdown source to an HTML fragment (offline, no network). */
class MarkdownRenderer @Inject constructor() {
    private val parser: Parser = Parser.builder().build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder().build()

    fun toHtml(markdown: String): String =
        if (markdown.isBlank()) "" else renderer.render(parser.parse(markdown)).trim()
}
