package io.github.martindavinci.lunaris.pdf.viewer

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The two directions of the conversation with the viewer page. */
interface ViewerChannel {
    val events: Flow<ViewerEvent>
    fun send(command: ViewerCommand)

    /** Only the page's own origin and main frame are heard. False if this WebView is too old for that. */
    fun attach(view: WebView): Boolean

    /** Nothing happens if another view has taken its place: a replaced view is released after the new one attaches. */
    fun detach(view: WebView)

    /** Failures the page cannot report itself, such as its renderer dying. */
    fun report(event: ViewerEvent)
}

/** Talks to the viewer page in the WebView that is currently on screen, if any. */
class ViewerEngine @Inject constructor() : ViewerChannel {
    private val incoming = MutableSharedFlow<ViewerEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var webView: WebView? = null

    override val events: Flow<ViewerEvent> = incoming.asSharedFlow()

    override fun send(command: ViewerCommand) {
        webView?.evaluateJavascript(ViewerProtocol.encode(command), null)
    }

    @SuppressLint("RequiresFeature")
    override fun attach(view: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        WebViewCompat.addWebMessageListener(view, HOST_OBJECT, setOf(ORIGIN)) { _, message, _, isMainFrame, _ ->
            if (isMainFrame) message.data?.let(ViewerProtocol::decode)?.let(::report)
        }
        webView = view
        return true
    }

    override fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    override fun report(event: ViewerEvent) {
        incoming.tryEmit(event)
    }

    private companion object {
        const val HOST_OBJECT = "lunarisHost"
        const val ORIGIN = "https://${ViewerRequestHandler.VIRTUAL_HOST}"
        const val EVENT_BUFFER = 64
    }
}
