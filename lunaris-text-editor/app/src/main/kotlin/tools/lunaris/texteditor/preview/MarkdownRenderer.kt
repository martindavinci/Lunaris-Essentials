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
    // escapeHtml: the preview WebView runs JS (for highlight.js), so neutralize any raw HTML
    // (e.g. <script>) embedded in the Markdown source instead of passing it through.
    private val renderer: HtmlRenderer =
        HtmlRenderer.builder().extensions(extensions).escapeHtml(true).build()

    fun toHtml(markdown: String): String =
        if (markdown.isBlank()) "" else renderer.render(parser.parse(separateTables(markdown))).trim()

    /**
     * GFM requires a table to start its own block, i.e. be preceded by a blank line. Many real
     * documents put a table header directly under a paragraph; commonmark then treats the header
     * row as plain paragraph text. This inserts the missing blank line before such headers so the
     * table renders. Only triggers on a pipe header immediately followed by a delimiter row
     * (`| --- | --- |`), so `---` thematic breaks / setext headings are left untouched.
     */
    private fun separateTables(markdown: String): String {
        val lines = markdown.split("\n")
        val out = ArrayList<String>(lines.size + 4)
        for (i in lines.indices) {
            if (i + 1 < lines.size && isPipeRow(lines[i]) && isDelimiterRow(lines[i + 1]) &&
                out.isNotEmpty() && out.last().isNotBlank()
            ) {
                out.add("")
            }
            out.add(lines[i])
        }
        return out.joinToString("\n")
    }

    private fun isPipeRow(s: String): Boolean = s.isNotBlank() && s.contains('|')

    private fun isDelimiterRow(s: String): Boolean {
        val t = s.trim()
        return t.contains('|') && t.contains('-') && DELIMITER.matches(t)
    }

    private companion object {
        // A table delimiter row: optional pipes around cells of dashes with optional :alignment:.
        val DELIMITER = Regex("^\\|?\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)*\\|?\\s*$")
    }
}
