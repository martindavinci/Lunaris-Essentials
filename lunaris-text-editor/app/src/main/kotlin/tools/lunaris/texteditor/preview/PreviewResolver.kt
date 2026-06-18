package tools.lunaris.texteditor.preview

object PreviewResolver {

    fun toHtmlDocument(
        fileName: String?,
        text: String,
        renderMarkdown: (String) -> String,
        dark: Boolean = false,
        highlightCss: String? = null,
        highlightJs: String? = null,
    ): String {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "md", "markdown" -> wrap(renderMarkdown(text), dark, highlightCss, highlightJs)
            in RAW_HTML_EXTS -> text
            in PLAIN_EXTS -> wrap("<pre>" + escapeHtml(text) + "</pre>", dark, highlightCss, highlightJs)
            // Code files: tag with the extension so highlight.js colorizes them (unknown languages
            // degrade gracefully to plain text).
            else -> wrap(
                "<pre><code class=\"language-$ext\">" + escapeHtml(text) + "</code></pre>",
                dark, highlightCss, highlightJs,
            )
        }
    }

    // Extensions (and the no-extension case) treated as prose, never syntax-highlighted.
    private val PLAIN_EXTS = setOf("", "txt", "text", "log")

    // Rendered as-is (the user's own web page); these are the only docs that need JS kept OFF,
    // since we don't sanitize their contents. Our generated docs run the bundled highlight.js.
    private val RAW_HTML_EXTS = setOf("html", "htm", "xhtml")

    /** True if [fileName] is rendered as raw HTML passthrough (so the WebView must disable JS). */
    fun isRawHtml(fileName: String?): Boolean =
        fileName?.substringAfterLast('.', "")?.lowercase() in RAW_HTML_EXTS

    private fun wrap(body: String, dark: Boolean, highlightCss: String?, highlightJs: String?): String {
        val theme = if (dark) "body{background:#121212;color:#e6e6e6;}a{color:#8ab4f8;}"
        else "body{background:#ffffff;color:#1a1a1a;}a{color:#1a56db;}"
        // Inject highlight.js only when there's a code block to colorize (keeps prose docs light).
        val highlight = highlightJs != null && highlightCss != null && body.contains("<code")
        val hlStyle = if (highlight) "<style>$highlightCss</style>" else ""
        val hlScript = if (highlight) "<script>$highlightJs</script><script>hljs.highlightAll();</script>" else ""
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>$STYLE$theme</style>$hlStyle</head><body>" +
            body + hlScript + "</body></html>"
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
