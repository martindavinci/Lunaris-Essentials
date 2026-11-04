package io.github.martindavinci.lunaris.pdf.pages

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.martindavinci.lunaris.pdf.viewer.ViewerRequestHandler
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Talks to the page in the WebView that lives as long as a job. */
class WorkEngine @Inject constructor() : WorkChannel {
    private val incoming = MutableSharedFlow<WorkEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var webView: WebView? = null

    override val events: Flow<WorkEvent> = incoming.asSharedFlow()

    override fun send(command: WorkCommand) {
        webView?.evaluateJavascript(WorkProtocol.encode(command), null)
    }

    override fun report(event: WorkEvent) {
        incoming.tryEmit(event)
    }

    @SuppressLint("RequiresFeature")
    override fun attach(view: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        WebViewCompat.addWebMessageListener(view, HOST_OBJECT, setOf(ORIGIN)) { _, message, _, isMainFrame, _ ->
            if (isMainFrame) message.data?.let(WorkProtocol::decode)?.let(::report)
        }
        webView = view
        return true
    }

    override fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    private companion object {
        const val HOST_OBJECT = "lunarisHost"
        const val ORIGIN = "https://${ViewerRequestHandler.VIRTUAL_HOST}"
        const val EVENT_BUFFER = 64
    }
}
