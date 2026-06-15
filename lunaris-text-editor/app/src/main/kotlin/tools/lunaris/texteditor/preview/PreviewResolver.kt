package tools.lunaris.texteditor.preview

object PreviewResolver {

    fun toHtmlDocument(
        fileName: String?,
        text: String,
        renderMarkdown: (String) -> String,
        dark: Boolean = false,
    ): String {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "md", "markdown" -> wrap(renderMarkdown(text), dark)
            "html", "htm", "xhtml" -> text
            else -> wrap("<pre>" + escapeHtml(text) + "</pre>", dark)
        }
    }

    private fun wrap(body: String, dark: Boolean): String {
        val theme = if (dark) "body{background:#121212;color:#e6e6e6;}a{color:#8ab4f8;}"
        else "body{background:#ffffff;color:#1a1a1a;}a{color:#1a56db;}"
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>$STYLE$theme</style></head><body>" +
            body + "</body></html>"
    }

    // Minimal stylesheet: readable body, bordered GFM tables, and visible task-list checkboxes.
    private val STYLE =
        "body{font-family:sans-serif;line-height:1.5;padding:16px;word-wrap:break-word;}" +
            "pre{white-space:pre-wrap;word-wrap:break-word;background:rgba(127,127,127,0.12);" +
            "padding:8px;border-radius:4px;}" +
            "code{background:rgba(127,127,127,0.12);padding:1px 4px;border-radius:3px;}" +
            "pre code{background:none;padding:0;}" +
            "table{border-collapse:collapse;margin:8px 0;}" +
            "th,td{border:1px solid rgba(127,127,127,0.5);padding:6px 10px;}" +
            "th{background:rgba(127,127,127,0.15);}" +
            "img{max-width:100%;}" +
            "ul.contains-task-list{list-style:none;padding-left:1em;}" +
            "li.task-list-item input{margin-right:0.5em;}"

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
