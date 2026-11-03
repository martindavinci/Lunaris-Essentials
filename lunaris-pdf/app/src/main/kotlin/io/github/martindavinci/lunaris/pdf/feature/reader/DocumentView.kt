package io.github.martindavinci.lunaris.pdf.feature.reader

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.github.martindavinci.lunaris.pdf.viewer.ContentDocumentSource
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEvent
import io.github.martindavinci.lunaris.pdf.viewer.ViewerRequestHandler
import io.github.martindavinci.lunaris.pdf.viewer.ViewerResponse
import java.io.ByteArrayInputStream
import java.io.IOException

/** The WebView that shows the document. The page inside it can reach nothing but [ViewerRequestHandler]. */
@Composable
internal fun DocumentView(uri: String, viewer: ViewerChannel, modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val handler = remember(uri) { requestHandler(appContext, uri.toUri()) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // With wrap-content the page would see a viewport as tall as its content: none.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                configure()
                keepHolderVisibleToAccessibility()
                setBackgroundColor(PageBackground.toArgb())
                webViewClient = ViewerClient(handler, viewer)
                if (viewer.attach(this)) {
                    loadUrl(ViewerRequestHandler.VIEWER_URL)
                } else {
                    viewer.report(ViewerEvent.LoadFailed)
                }
            }
        },
        onRelease = { view ->
            viewer.detach()
            view.destroy()
        },
    )
}

/**
 * While an accessibility service runs, Compose marks the holder of an AndroidView as not visible, so that
 * TalkBack does not stop on it. Services that read the tree from the top, like Select to Speak, then skip
 * everything inside it and find no text in the document. Compose sets this as the holder attaches: hence the post.
 */
private fun View.keepHolderVisibleToAccessibility() {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            view.post {
                val holder = view.parent as? View ?: return@post
                val fromCompose = ViewCompat.getAccessibilityDelegate(holder)
                ViewCompat.setAccessibilityDelegate(holder, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        if (fromCompose != null) {
                            fromCompose.onInitializeAccessibilityNodeInfo(host, info)
                        } else {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                        }
                        info.isVisibleToUser = true
                    }
                })
            }
        }

        override fun onViewDetachedFromWindow(view: View) = Unit
    })
}

private fun requestHandler(context: Context, uri: Uri): ViewerRequestHandler {
    val source = ContentDocumentSource(context.contentResolver, uri)
    return ViewerRequestHandler(
        assets = { name ->
            try {
                context.assets.open(name)
            } catch (_: IOException) {
                null
            }
        },
        document = { source },
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
        setSupportZoom(false)
        builtInZoomControls = false
        setGeolocationEnabled(false)
    }
}

private class ViewerClient(
    private val handler: ViewerRequestHandler,
    private val viewer: ViewerChannel,
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val host = url.host.orEmpty().takeIf { url.scheme == HTTPS }.orEmpty()
        val range = request.requestHeaders.entries.firstOrNull { it.key.equals(RANGE, ignoreCase = true) }?.value
        return handler.handle(host, url.path.orEmpty(), range).toWebResponse()
    }

    /** The page loads once and stays: nothing in a document can navigate this WebView. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    /** Returning true keeps the app alive. The view is replaced: by a new one, or by the error message after a crash. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        viewer.report(if (detail.didCrash()) ViewerEvent.LoadFailed else ViewerEvent.RendererKilled)
        return true
    }

    private fun ViewerResponse.toWebResponse() = WebResourceResponse(
        mime,
        if (mime.startsWith(TEXT_MIME_PREFIX)) UTF_8 else null,
        status,
        REASONS[status] ?: DEFAULT_REASON,
        headers,
        body ?: ByteArrayInputStream(ByteArray(0)),
    )

    private companion object {
        const val HTTPS = "https"
        const val RANGE = "Range"
        const val UTF_8 = "utf-8"
        const val TEXT_MIME_PREFIX = "text/"
        const val DEFAULT_REASON = "OK"
        val REASONS = mapOf(
            206 to "Partial Content",
            403 to "Forbidden",
            404 to "Not Found",
            416 to "Range Not Satisfiable",
        )
    }
}
