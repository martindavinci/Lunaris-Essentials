package tools.lunaris.texteditor.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Sentence/line playback state machine over a [SpeechEngine]. Advances on each segment's
 *  onSegmentDone; TTS has no native pause, so pause = stop + keep index, resume = re-speak current.
 *  Rate/voice changes re-speak the current segment so they apply immediately. */
@Singleton
class DocumentReader @Inject constructor(private val engine: SpeechEngine) {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    /** Invoked when playback can't start (no engine/voice). */
    var onUnavailable: (() -> Unit)? = null

    private var segments: List<String> = emptyList()

    init {
        engine.onSegmentDone = ::handleDone
        engine.onUnavailable = {
            _state.value = ReaderState()
            onUnavailable?.invoke()
        }
    }

    fun start(segments: List<String>, rate: Float, voiceId: String?) {
        this.segments = segments
        engine.setRate(rate)
        engine.setVoice(voiceId)
        _state.value = ReaderState(
            reading = segments.isNotEmpty(), paused = false,
            index = 0, total = segments.size, rate = rate, voiceId = voiceId,
        )
        if (segments.isNotEmpty()) speakCurrent()
    }

    fun togglePause() {
        val s = _state.value
        if (!s.reading) return
        if (s.paused) { _state.value = s.copy(paused = false); speakCurrent() }
        else { engine.stop(); _state.value = s.copy(paused = true) }
    }

    fun next() = moveTo(_state.value.index + 1)
    fun previous() = moveTo(_state.value.index - 1)

    fun setRate(rate: Float) {
        engine.setRate(rate)
        val s = _state.value.copy(rate = rate)
        _state.value = s
        if (s.reading && !s.paused) speakCurrent()
    }

    fun setVoice(voiceId: String?) {
        engine.setVoice(voiceId)
        val s = _state.value.copy(voiceId = voiceId)
        _state.value = s
        if (s.reading && !s.paused) speakCurrent()
    }

    fun stop() {
        engine.stop()
        segments = emptyList()
        _state.value = ReaderState()
    }

    fun shutdown() = engine.shutdown()

    private fun moveTo(target: Int) {
        val s = _state.value
        if (!s.reading || s.total == 0) return
        val i = target.coerceIn(0, s.total - 1)
        _state.value = s.copy(index = i)
        if (!s.paused) speakCurrent()
    }

    private fun speakCurrent() {
        val i = _state.value.index
        engine.speak(segments[i], i.toString())
    }

    private fun handleDone(utteranceId: String) {
        val s = _state.value
        if (!s.reading || s.paused || utteranceId != s.index.toString()) return
        val nextIndex = s.index + 1
        if (nextIndex < s.total) { _state.value = s.copy(index = nextIndex); speakCurrent() }
        else _state.value = s.copy(reading = false, paused = false)
    }
}
