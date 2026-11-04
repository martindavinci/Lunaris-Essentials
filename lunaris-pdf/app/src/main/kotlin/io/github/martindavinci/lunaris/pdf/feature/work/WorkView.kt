package io.github.martindavinci.lunaris.pdf.feature.work

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import io.github.martindavinci.lunaris.pdf.pages.WorkEngine
import io.github.martindavinci.lunaris.pdf.pages.WorkEvent
import io.github.martindavinci.lunaris.pdf.viewer.ContentDocumentSource
import io.github.martindavinci.lunaris.pdf.viewer.ViewerRequestHandler
import java.io.ByteArrayInputStream
import java.io.IOException

private val HiddenSize = 1.dp

/** The page that makes the files: unseen, and only there while a job runs. It can reach nothing but [ViewerRequestHandler]. */
@Composable
internal fun WorkView(sources: List<String>, engine: WorkEngine) {
    val appContext = LocalContext.current.applicationContext
    val handler = remember(sources) { requestHandler(appContext, sources.map { it.toUri() }) }
    AndroidView(
        modifier = Modifier.size(HiddenSize).alpha(0f),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                configure()
                webViewClient = WorkClient(handler, engine)
                if (engine.attach(this)) {
                    loadUrl(ViewerRequestHandler.WORK_URL)
                } else {
                    engine.report(WorkEvent.RendererGone)
                }
            }
        },
        onRelease = { view ->
            engine.detach(view)
            view.destroy()
        },
    )
}

private fun requestHandler(context: Context, sources: List<Uri>): ViewerRequestHandler {
    val readers = sources.map { ContentDocumentSource(context.contentResolver, it) }
    return ViewerRequestHandler(
        assets = { name ->
            try {
                context.assets.open(name)
            } catch (_: IOException) {
                null
            }
        },
        document = { null },
        source = { readers.getOrNull(it) },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configure() {
    settings.apply {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = false
        databaseEnabled = false
        cacheMode = WebSettings.LOAD_NO_CACHE
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
    }
}

private class WorkClient(private val handler: ViewerRequestHandler, private val engine: WorkEngine) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val host = url.host.orEmpty().takeIf { url.scheme == HTTPS }.orEmpty()
        val response = handler.handle(host, url.path.orEmpty(), null)
        return WebResourceResponse(
            response.mime,
            if (response.mime.startsWith(TEXT_MIME_PREFIX)) UTF_8 else null,
            response.status,
            REASONS[response.status] ?: DEFAULT_REASON,
            response.headers,
            response.body ?: ByteArrayInputStream(ByteArray(0)),
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    /** Returning true keeps the app alive. The job ends with a message. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        engine.report(WorkEvent.RendererGone)
        return true
    }

    private companion object {
        const val HTTPS = "https"
        const val UTF_8 = "utf-8"
        const val TEXT_MIME_PREFIX = "text/"
        const val DEFAULT_REASON = "OK"
        val REASONS = mapOf(403 to "Forbidden", 404 to "Not Found")
    }
}
