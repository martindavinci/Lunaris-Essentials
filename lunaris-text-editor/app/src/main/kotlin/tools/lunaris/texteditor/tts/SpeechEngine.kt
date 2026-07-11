package tools.lunaris.texteditor.tts

/** Abstraction over the platform TTS so [DocumentReader] is unit-testable. One call to [speak]
 *  voices one segment (chunked internally if over the engine limit) and fires [onSegmentDone] with
 *  the SAME [utteranceId] when that segment finishes. */
interface SpeechEngine {
    fun speak(text: String, utteranceId: String)
    fun stop()
    fun setRate(rate: Float)
    fun setVoice(voiceId: String?)
    fun voices(): List<VoiceInfo>
    fun shutdown()
    var onSegmentDone: ((String) -> Unit)?
    var onUnavailable: (() -> Unit)?
}

/** Immutable snapshot driving the reading UI. */
data class ReaderState(
    val reading: Boolean = false,
    val paused: Boolean = false,
    val index: Int = 0,
    val total: Int = 0,
    val rate: Float = 1f,
    val voiceId: String? = null,
)
