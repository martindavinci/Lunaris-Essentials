package tools.lunaris.texteditor.preview

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import javax.inject.Inject

/** Converts Markdown source to an HTML fragment (offline, no network).
 *  Supports GFM tables and task-list checkboxes. */
class MarkdownRenderer @Inject constructor() {
    private val extensions = listOf(
        TablesExtension.create(),
        TaskListItemsExtension.create(),
    )
    private val parser: Parser = Parser.builder().extensions(extensions).build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder().extensions(extensions).build()

    fun toHtml(markdown: String): String =
        if (markdown.isBlank()) "" else renderer.render(parser.parse(markdown)).trim()
}
