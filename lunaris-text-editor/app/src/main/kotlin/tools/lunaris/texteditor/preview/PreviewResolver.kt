package tools.lunaris.texteditor.preview

object PreviewResolver {

    fun toHtmlDocument(fileName: String?, text: String, renderMarkdown: (String) -> String): String {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "md", "markdown" -> wrap(renderMarkdown(text))
            "html", "htm", "xhtml" -> text
            else -> wrap("<pre>" + escapeHtml(text) + "</pre>")
        }
    }

    private fun wrap(body: String): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body>" +
            body + "</body></html>"

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
