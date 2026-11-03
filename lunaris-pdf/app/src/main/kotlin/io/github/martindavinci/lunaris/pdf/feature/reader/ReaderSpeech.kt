package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.tts.ReadAloudController
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState
import io.github.martindavinci.lunaris.pdf.tts.SpeechEngine
import io.github.martindavinci.lunaris.pdf.viewer.ViewerChannel
import io.github.martindavinci.lunaris.pdf.viewer.ViewerCommand
import io.github.martindavinci.lunaris.pdf.viewer.ViewerEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Reading aloud for the Reader. The engine starts on first use: it is slow to start and most reading needs none. */
internal class ReaderSpeech(
    private val scope: CoroutineScope,
    private val viewer: ViewerChannel,
    private val speechEngine: () -> SpeechEngine,
    private val onState: (ReadAloudState) -> Unit,
) {
    private var controller: ReadAloudController? = null
    private val waiting = mutableMapOf<Int, CompletableDeferred<String>>()

    fun start(page: Int, pages: Int) {
        val current = controller ?: ReadAloudController(speechEngine(), ::fetchPage, scope).also { made ->
            controller = made
            scope.launch { made.state.collect(onState) }
        }
        current.start(page, pages)
    }

    fun pause() = controller?.pause()
    fun resume() = controller?.resume()
    fun next() = controller?.next()
    fun previous() = controller?.previous()
    fun stop() = controller?.stop()
    fun shutdown() = controller?.shutdown()

    fun onPageText(event: ViewerEvent.PageText) {
        waiting.remove(event.page)?.complete(event.text)
    }

    private suspend fun fetchPage(page: Int): String {
        val answer = CompletableDeferred<String>()
        waiting[page] = answer
        viewer.send(ViewerCommand.ReadPageText(page))
        return answer.await()
    }
}
