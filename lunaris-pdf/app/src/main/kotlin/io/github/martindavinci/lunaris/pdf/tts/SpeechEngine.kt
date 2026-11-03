package io.github.martindavinci.lunaris.pdf.tts

import java.util.Locale

/**
 * The device's text to speech behind an interface, so that what drives it can be tested. One call
 * to [speak] voices one segment, in pieces if it is over the engine's limit, and fires
 * [onSegmentDone] with the same [utteranceId] when it finishes. The callbacks arrive on a background
 * thread.
 */
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

/** A device voice, reduced to the fields Android exposes. */
data class VoiceInfo(val id: String, val locale: Locale, val isNetwork: Boolean)
