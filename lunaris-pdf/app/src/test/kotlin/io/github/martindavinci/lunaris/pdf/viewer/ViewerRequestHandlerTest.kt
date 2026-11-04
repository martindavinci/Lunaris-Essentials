package io.github.martindavinci.lunaris.pdf.viewer

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Test

class ViewerRequestHandlerTest {
    private val bytes = ByteArray(150) { it.toByte() }

    private fun source(size: Long? = 150L) = object : DocumentSource {
        override fun size() = size
        override fun open(start: Long): InputStream = ByteArrayInputStream(bytes, start.toInt(), bytes.size - start.toInt())
    }

    private fun handler(document: DocumentSource? = source()) = ViewerRequestHandler(
        assets = { name -> if (name in ASSETS) ByteArrayInputStream(name.toByteArray()) else null },
        document = { document },
    )

    private fun ViewerResponse.bodyBytes() = checkNotNull(body).use { it.readBytes() }

    @Test fun aSourceIsServedFromItsOwnPath() {
        val handler = ViewerRequestHandler(assets = { null }, document = { null }, source = { if (it == 1) source() else null })
        val response = handler.handle(ViewerRequestHandler.VIRTUAL_HOST, "/source/1", null)
        assertThat(response.status).isEqualTo(200)
        assertThat(response.bodyBytes()).isEqualTo(bytes)
    }

    @Test fun aSourceThatIsNotThereIsNotFound() {
        val handler = ViewerRequestHandler(assets = { null }, document = { null }, source = { if (it == 1) source() else null })
        listOf("/source/0", "/source/x", "/source/", "/source/1/..").forEach { path ->
            assertThat(handler.handle(ViewerRequestHandler.VIRTUAL_HOST, path, null).status).isEqualTo(404)
        }
    }

    @Test fun otherHost_gets403() {
        assertThat(handler().handle("example.com", "/x", null).status).isEqualTo(403)
    }

    @Test fun otherHost_cannotReachTheDocument() {
        assertThat(handler().handle("example.com", "/document.pdf", null).body).isNull()
    }

    @Test fun rangeRequest_gets206WithContentRange() {
        val response = handler().handle(HOST, "/document.pdf", "bytes=100-199")
        assertThat(response.status).isEqualTo(206)
        assertThat(response.headers["Content-Range"]).isEqualTo("bytes 100-149/150")
        assertThat(response.headers["Accept-Ranges"]).isEqualTo("bytes")
        assertThat(response.headers["Content-Length"]).isEqualTo("50")
        assertThat(response.bodyBytes()).isEqualTo(bytes.copyOfRange(100, 150))
    }

    @Test fun rangeRequest_stopsAtTheEndOfTheRange() {
        assertThat(handler().handle(HOST, "/document.pdf", "bytes=10-19").bodyBytes())
            .isEqualTo(bytes.copyOfRange(10, 20))
    }

    /** WebView applies the Range header to the stream too, with available() and skip(): it must change nothing. */
    @Test fun rangeBody_staysPutWhenTheWebViewSeeksInIt() {
        val body = checkNotNull(handler().handle(HOST, "/document.pdf", "bytes=100-109").body)
        assertThat(body.available()).isEqualTo(0)
        assertThat(body.skip(100)).isEqualTo(100)
        assertThat(body.readBytes()).isEqualTo(bytes.copyOfRange(100, 110))
    }

    @Test fun plainRequest_getsTheWholeDocumentAndOffersRanges() {
        val response = handler().handle(HOST, "/document.pdf", null)
        assertThat(response.status).isEqualTo(200)
        assertThat(response.mime).isEqualTo("application/pdf")
        assertThat(response.headers["Accept-Ranges"]).isEqualTo("bytes")
        assertThat(response.headers["Content-Length"]).isEqualTo("150")
        assertThat(response.bodyBytes()).isEqualTo(bytes)
    }

    @Test fun documentIsNeverCached_becauseTheSamePathServesEveryDocument() {
        assertThat(handler().handle(HOST, "/document.pdf", null).headers["Cache-Control"]).isEqualTo("no-store")
    }

    @Test fun documentWithUnknownSize_isServedWhole() {
        val response = handler(source(size = null)).handle(HOST, "/document.pdf", "bytes=0-9")
        assertThat(response.status).isEqualTo(200)
        assertThat(response.headers).doesNotContainKey("Accept-Ranges")
        assertThat(response.headers).doesNotContainKey("Content-Length")
        assertThat(response.bodyBytes()).isEqualTo(bytes)
    }

    @Test fun rangePastTheEnd_gets416() {
        val response = handler().handle(HOST, "/document.pdf", "bytes=200-")
        assertThat(response.status).isEqualTo(416)
        assertThat(response.headers["Content-Range"]).isEqualTo("bytes */150")
    }

    @Test fun missingDocument_gets404() {
        assertThat(handler(document = null).handle(HOST, "/document.pdf", null).status).isEqualTo(404)
    }

    @Test fun documentThatCannotBeOpened_gets404() {
        val broken = object : DocumentSource {
            override fun size() = 150L
            override fun open(start: Long): InputStream = throw IOException("gone")
        }
        assertThat(handler(broken).handle(HOST, "/document.pdf", null).status).isEqualTo(404)
    }

    @Test fun documentThatIsNoLongerAllowed_gets404() {
        val revoked = object : DocumentSource {
            override fun size() = 150L
            override fun open(start: Long): InputStream = throw SecurityException("revoked")
        }
        assertThat(handler(revoked).handle(HOST, "/document.pdf", null).status).isEqualTo(404)
    }

    @Test fun mjsAndWasm_getTheirMimeTypes() {
        assertThat(handler().handle(HOST, "/pdf.min.mjs", null).mime).isEqualTo("text/javascript")
        assertThat(handler().handle(HOST, "/wasm/qcms_bg.wasm", null).mime).isEqualTo("application/wasm")
    }

    @Test fun asset_isServedFromTheViewerFolder() {
        val response = handler().handle(HOST, "/pdf.min.mjs", null)
        assertThat(response.status).isEqualTo(200)
        assertThat(String(response.bodyBytes())).isEqualTo("viewer/pdf.min.mjs")
    }

    @Test fun viewerPage_carriesItsContentSecurityPolicy() {
        val policy = handler().handle(HOST, "/viewer.html", null).headers["Content-Security-Policy"]
        assertThat(policy).contains("default-src 'self'")
        assertThat(policy).contains("script-src 'self' 'wasm-unsafe-eval'")
        assertThat(policy).doesNotContain(" 'unsafe-eval'")
    }

    @Test fun missingAsset_gets404() {
        assertThat(handler().handle(HOST, "/nothing.js", null).status).isEqualTo(404)
    }

    @Test fun pathsCannotLeaveTheViewerAssets() {
        listOf("/../x", "/a/../../x", "/..", "/./x", "//x", "/a\\..\\x", "", "x").forEach {
            assertThat(handler().handle(HOST, it, null).status).isEqualTo(404)
        }
    }

    private companion object {
        const val HOST = "appassets.androidplatform.net"
        val ASSETS = setOf("viewer/viewer.html", "viewer/pdf.min.mjs", "viewer/wasm/qcms_bg.wasm", "viewer/x")
    }
}
