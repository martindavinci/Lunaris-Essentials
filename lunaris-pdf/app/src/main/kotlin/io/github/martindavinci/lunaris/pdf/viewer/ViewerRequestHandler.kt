package io.github.martindavinci.lunaris.pdf.viewer

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** What the WebView gets for a request. */
data class ViewerResponse(
    val status: Int,
    val mime: String,
    val headers: Map<String, String> = emptyMap(),
    val body: InputStream? = null,
)

/**
 * The whole web server of the viewer: the page and pdf.js from the assets, and the open document
 * at a fixed path. Only the virtual host answers, so nothing a PDF contains can reach anything else.
 */
class ViewerRequestHandler(
    private val assets: (String) -> InputStream?,
    private val document: () -> DocumentSource?,
) {
    fun handle(host: String, path: String, range: String?): ViewerResponse = when {
        host != VIRTUAL_HOST -> ViewerResponse(STATUS_FORBIDDEN, MIME_TEXT)
        path == DOCUMENT_PATH -> serveDocument(range)
        else -> serveAsset(path)
    }

    private fun serveAsset(path: String): ViewerResponse {
        val segments = path.removePrefix("/").split('/')
        val safe = path.startsWith('/') && segments.none { it.isEmpty() || it == "." || it == ".." || '\\' in it }
        val body = if (safe) assets(ASSET_ROOT + segments.joinToString("/")) else null
        body ?: return notFound()
        val headers = buildMap {
            put(HEADER_NO_SNIFF, "nosniff")
            if (path.endsWith(".html")) put(HEADER_CSP, CONTENT_SECURITY_POLICY)
        }
        return ViewerResponse(STATUS_OK, mimeOf(path), headers, body)
    }

    private fun serveDocument(range: String?): ViewerResponse {
        val source = document() ?: return notFound()
        return try {
            val size = source.size()
            when {
                size == null -> ok(source, emptyMap())
                range == null -> ok(source, mapOf(HEADER_RANGES to "bytes", HEADER_LENGTH to size.toString()))
                else -> partial(source, range, size)
            }
        } catch (_: IOException) {
            notFound()
        } catch (_: SecurityException) {
            notFound()
        }
    }

    private fun ok(source: DocumentSource, headers: Map<String, String>) =
        ViewerResponse(STATUS_OK, MIME_PDF, headers + (HEADER_CACHE to "no-store"), source.open(0))

    private fun partial(source: DocumentSource, header: String, size: Long): ViewerResponse {
        val range = ByteRange.parse(header, size)
            ?: return ViewerResponse(STATUS_BAD_RANGE, MIME_PDF, mapOf(HEADER_CONTENT_RANGE to "bytes */$size"))
        val headers = mapOf(
            HEADER_RANGES to "bytes",
            HEADER_LENGTH to range.length.toString(),
            HEADER_CONTENT_RANGE to "bytes ${range.start}-${range.endInclusive}/$size",
            HEADER_CACHE to "no-store",
        )
        return ViewerResponse(STATUS_PARTIAL, MIME_PDF, headers, LimitedStream(source.open(range.start), range.length))
    }

    private fun notFound() = ViewerResponse(STATUS_NOT_FOUND, MIME_TEXT)

    private fun mimeOf(path: String): String = MIME_BY_EXTENSION[path.substringAfterLast('.')] ?: MIME_BINARY

    /**
     * Stops after [remaining] bytes, however many the underlying stream has. WebView applies the Range
     * header to any stream as well, asking its size with [available] and moving with [skip]; this one
     * is already in place, so both say there is nothing to do.
     */
    private class LimitedStream(stream: InputStream, private var remaining: Long) : FilterInputStream(stream) {
        override fun available(): Int = 0

        override fun skip(count: Long): Long = count

        override fun read(): Int {
            if (remaining <= 0) return END
            return super.read().also { if (it != END) remaining-- }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return END
            val count = super.read(buffer, offset, length.toLong().coerceAtMost(remaining).toInt())
            if (count > 0) remaining -= count
            return count
        }
    }

    companion object {
        const val VIRTUAL_HOST = "appassets.androidplatform.net"
        const val VIEWER_URL = "https://$VIRTUAL_HOST/viewer.html"
        const val DOCUMENT_PATH = "/document.pdf"

        private const val END = -1
        private const val ASSET_ROOT = "viewer/"
        private const val STATUS_OK = 200
        private const val STATUS_PARTIAL = 206
        private const val STATUS_FORBIDDEN = 403
        private const val STATUS_NOT_FOUND = 404
        private const val STATUS_BAD_RANGE = 416
        private const val MIME_PDF = "application/pdf"
        private const val MIME_TEXT = "text/plain"
        private const val MIME_BINARY = "application/octet-stream"
        private const val HEADER_RANGES = "Accept-Ranges"
        private const val HEADER_LENGTH = "Content-Length"
        private const val HEADER_CONTENT_RANGE = "Content-Range"
        private const val HEADER_CACHE = "Cache-Control"
        private const val HEADER_CSP = "Content-Security-Policy"
        private const val HEADER_NO_SNIFF = "X-Content-Type-Options"
        private const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' blob: data:; font-src 'self' blob: data:; worker-src 'self' blob:; " +
                "connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'"
        private val MIME_BY_EXTENSION = mapOf(
            "html" to "text/html",
            "mjs" to "text/javascript",
            "js" to "text/javascript",
            "css" to "text/css",
            "wasm" to "application/wasm",
            "svg" to "image/svg+xml",
        )
    }
}
