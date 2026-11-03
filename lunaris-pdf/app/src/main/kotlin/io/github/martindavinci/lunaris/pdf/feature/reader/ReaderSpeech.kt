package io.github.martindavinci.lunaris.pdf.feature.reader

import io.github.martindavinci.lunaris.pdf.settings.SpeechPreferences
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudController
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState
import io.github.martindavinci.lunaris.pdf.tts.SpeechEngine
import io.github.martindavinci.lunaris.pdf.tts.SpeechRates
import io.github.martindavinci.lunaris.pdf.tts.SpokenSentence
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
    private val prefs: SpeechPreferences,
    private val update: ((ReaderUiState) -> ReaderUiState) -> Unit,
    private val onState: (ReadAloudState) -> Unit,
) {
    private var controller: ReadAloudController? = null
    private var engine: SpeechEngine? = null
    private var rate = SpeechRates.NORMAL
    private var voice: String? = null
    private val waiting = mutableMapOf<Int, CompletableDeferred<String>>()

    init {
        scope.launch { prefs.speechRate.collect(::onRate) }
        scope.launch { prefs.speechVoice.collect(::onVoice) }
    }

    fun start(page: Int, pages: Int) {
        val current = controller ?: ReadAloudController(madeEngine(), ::fetchPage, scope).also { made ->
            controller = made
            scope.launch { made.state.collect(onState) }
            scope.launch { made.sentence.collect(::onSentence) }
        }
        current.start(page, pages)
    }

    fun pause() = controller?.pause()
    fun resume() = controller?.resume()
    fun next() = controller?.next()
    fun previous() = controller?.previous()
    fun nextSentence() = controller?.nextSentence()
    fun previousSentence() = controller?.previousSentence()
    fun stop() = controller?.stop()

    fun chooseRate(value: Float) {
        scope.launch { prefs.setSpeechRate(SpeechRates.snap(value)) }
    }

    fun chooseVoice(value: String?) {
        scope.launch { prefs.setSpeechVoice(value) }
    }

    fun shutdown() = controller?.shutdown()

    fun onPageText(event: ViewerEvent.PageText) {
        waiting.remove(event.page)?.complete(event.text)
    }

    private fun madeEngine(): SpeechEngine = speechEngine().also {
        it.setRate(rate)
        it.setVoice(voice)
        engine = it
    }

    private fun onRate(value: Float) {
        if (value == rate) return
        rate = value
        update { it.copy(readAloudRate = value) }
        controller?.setRate(value)
    }

    private fun onVoice(value: String?) {
        if (value == voice) return
        voice = value
        update { it.copy(readAloudVoice = value) }
        controller?.setVoice(value)
    }

    /** The first sentence says the engine is ready, which is when it knows its voices. */
    private fun onSentence(sentence: SpokenSentence?) {
        update { state ->
            state.copy(
                spoken = sentence,
                readAloudVoices = state.readAloudVoices.ifEmpty { if (sentence != null) engine?.voices().orEmpty() else emptyList() },
            )
        }
        viewer.send(sentence?.let { ViewerCommand.MarkSentence(it.page, it.text, it.occurrence) } ?: ViewerCommand.ClearMark)
    }

    private suspend fun fetchPage(page: Int): String {
        val answer = CompletableDeferred<String>()
        waiting[page] = answer
        viewer.send(ViewerCommand.ReadPageText(page))
        return answer.await()
    }
}
