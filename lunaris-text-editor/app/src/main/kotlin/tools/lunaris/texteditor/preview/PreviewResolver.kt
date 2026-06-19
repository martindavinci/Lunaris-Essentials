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
            // Raw HTML is shown as-is; in dark mode try a CSS-only color flip (images re-inverted).
            in RAW_HTML_EXTS -> if (dark) invertedHtml(text) else text
            in PLAIN_EXTS -> wrapCodeDocument("<pre>" + escapeHtml(text) + "</pre>", dark, highlightCss, highlightJs)
            // Code files: tag with the extension so highlight.js colorizes them (unknown languages
            // degrade gracefully to plain text).
            else -> wrapCodeDocument(
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

    /** Best-effort dark mode for a raw HTML page: invert the document, undo it on media. */
    private fun invertedHtml(text: String): String =
        "<style>html{filter:invert(1) hue-rotate(180deg);background:#ffffff;}" +
            "img,picture,video,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg);}</style>\n" + text

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

    /**
     * Full-page, single-background layout for code/plain previews (no card). One solid background
     * (dark or pure white per the theme toggle) so it stays clean on screen and when printed /
     * exported to PDF; the syntax colors are foreground only. Page is sized A4 for print.
     */
    private fun wrapCodeDocument(body: String, dark: Boolean, highlightCss: String?, highlightJs: String?): String {
        val bg = if (dark) "#1e1e1e" else "#ffffff"
        val fg = if (dark) "#e6e6e6" else "#1a1a1a"
        val highlight = highlightJs != null && highlightCss != null && body.contains("<code")
        val hlStyle = if (highlight) "<style>$highlightCss</style>" else ""
        val hlScript = if (highlight) "<script>$highlightJs</script><script>hljs.highlightAll();</script>" else ""
        // Our rules come AFTER the highlight.js theme so the solid background wins over its card bg.
        val style = "html,body{margin:0;padding:0;background:$bg;color:$fg;" +
            "-webkit-print-color-adjust:exact;print-color-adjust:exact;}" +
            "pre{margin:0;padding:16px;white-space:pre-wrap;word-wrap:break-word;" +
            "font-family:monospace;font-size:13px;line-height:1.4;}" +
            "pre,code,.hljs,pre code.hljs,code.hljs{background:$bg !important;}" +
            "pre code.hljs{padding:0 !important;}" +
            // Fill the whole A4 page with the chosen background (no white margin frame); the inset
            // becomes the pre padding instead of a page margin.
            "@media print{@page{size:A4;margin:0;}pre{padding:1.5cm;}}"
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "$hlStyle<style>$style</style></head><body>" +
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
