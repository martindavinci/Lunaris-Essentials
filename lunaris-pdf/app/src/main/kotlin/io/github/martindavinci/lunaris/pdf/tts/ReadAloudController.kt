package io.github.martindavinci.lunaris.pdf.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reads the document aloud a sentence at a time, page after page, skipping pages without text. The
 * engine has no pause, so pausing stops it and resuming speaks the current sentence again.
 */
class ReadAloudController(
    private val engine: SpeechEngine,
    private val fetchPage: suspend (Int) -> String,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<ReadAloudState>(ReadAloudState.Idle)
    val state: StateFlow<ReadAloudState> = mutableState.asStateFlow()

    private var pages = 0
    private var page = 1
    private var sentences = emptyList<String>()
    private var sentence = 0
    private var utterance = 0
    private var spokeSomething = false
    private var loading: Job? = null

    init {
        // Both arrive on the engine's thread.
        engine.onSegmentDone = { id -> scope.launch { onSegmentDone(id) } }
        engine.onUnavailable = { scope.launch { mutableState.value = ReadAloudState.Unavailable } }
    }

    fun start(page: Int, pages: Int) {
        this.pages = pages
        spokeSomething = false
        load(page)
    }

    fun pause() {
        if (state.value !is ReadAloudState.Reading) return
        silence()
        mutableState.value = ReadAloudState.Paused(page)
    }

    fun resume() {
        if (state.value !is ReadAloudState.Paused) return
        if (sentences.isEmpty()) {
            load(page)
        } else {
            mutableState.value = ReadAloudState.Reading(page)
            speak()
        }
    }

    fun next() {
        if (page < pages) load(page + 1)
    }

    fun previous() {
        if (page > 1) load(page - 1)
    }

    fun stop() {
        silence()
        mutableState.value = ReadAloudState.Idle
    }

    fun shutdown() {
        silence()
        engine.shutdown()
    }

    private fun load(target: Int) {
        silence()
        page = target
        sentences = emptyList()
        mutableState.value = ReadAloudState.Reading(target)
        loading = scope.launch { mutableState.value = readFrom(target) ?: return@launch }
    }

    /** Speaks the first page with text from [target] on. Null when it is speaking. */
    private suspend fun readFrom(target: Int): ReadAloudState? {
        var empty = 0
        for (candidate in target..pages) {
            val found = PageSentences.of(fetchPage(candidate))
            if (found.isNotEmpty()) {
                page = candidate
                sentences = found
                sentence = 0
                spokeSomething = true
                mutableState.value = ReadAloudState.Reading(candidate)
                speak()
                return null
            }
            empty++
            if (!spokeSomething && empty >= EMPTY_PAGES_MEANING_NO_TEXT) return ReadAloudState.NoText(target)
        }
        return if (spokeSomething) ReadAloudState.Idle else ReadAloudState.NoText(target)
    }

    private fun speak() {
        utterance++
        engine.speak(sentences[sentence], utterance.toString())
    }

    private fun silence() {
        loading?.cancel()
        utterance++
        engine.stop()
    }

    private fun onSegmentDone(id: String) {
        if (id != utterance.toString() || state.value !is ReadAloudState.Reading) return
        when {
            sentence < sentences.lastIndex -> {
                sentence++
                speak()
            }
            page < pages -> load(page + 1)
            else -> mutableState.value = ReadAloudState.Idle
        }
    }

    private companion object {
        const val EMPTY_PAGES_MEANING_NO_TEXT = 3
    }
}
