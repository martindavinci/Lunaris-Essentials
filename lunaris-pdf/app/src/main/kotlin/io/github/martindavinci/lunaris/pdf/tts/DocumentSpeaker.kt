package io.github.martindavinci.lunaris.pdf.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * [SpeechEngine] backed by on-device [TextToSpeech]. Init is async: a [speak] issued before the
 * engine is ready is queued and flushed once init completes; if there is no usable engine/voice,
 * [onUnavailable] fires. Long segments are chunked via [SpeechChunker] so none exceed the engine
 * input limit. Only this instance is configured — global/system TTS settings are never touched.
 */
class DocumentSpeaker(context: Context) : SpeechEngine {

    override var onSegmentDone: ((String) -> Unit)? = null
    override var onUnavailable: (() -> Unit)? = null

    private var settled = false
    private var usable = false
    private var pending: Pair<String, String>? = null   // text to segmentId, requested before init
    private var chosenVoice: String? = null
    private val progress = SegmentProgress { onSegmentDone?.invoke(it) }

    private val tts = TextToSpeech(context) { status ->
        settled = true
        usable = status == TextToSpeech.SUCCESS && selectDefaultVoice()
        if (usable) applyVoice()
        val queued = pending
        pending = null
        when {
            usable && queued != null -> enqueue(queued.first, queued.second)
            !usable && queued != null -> onUnavailable?.invoke()
        }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = progress.chunkFinished(utteranceId)
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {}
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "onError code=$errorCode")
                progress.chunkFailed(utteranceId)
            }
        })
    }

    override fun speak(text: String, utteranceId: String) {
        when {
            !settled -> pending = text to utteranceId
            usable -> enqueue(text, utteranceId)
            else -> onUnavailable?.invoke()
        }
    }

    override fun stop() {
        pending = null
        progress.clear()
        tts.stop()
    }

    override fun setRate(rate: Float) { tts.setSpeechRate(rate) }

    /** Before the engine is ready this only remembers the choice. A voice no longer there means the default. */
    override fun setVoice(voiceId: String?) {
        chosenVoice = voiceId
        if (usable) {
            if (voiceId == null) selectDefaultVoice()
            applyVoice()
        }
    }

    private fun applyVoice() {
        val id = chosenVoice ?: return
        tts.voices?.firstOrNull { it.name == id }?.let { tts.voice = it }
    }

    override fun voices(): List<VoiceInfo> =
        tts.voices.orEmpty()
            .filter { it.locale != null }
            .map { VoiceInfo(it.name, it.locale, it.isNetworkConnectionRequired) }

    override fun shutdown() {
        pending = null
        onSegmentDone = null
        onUnavailable = null
        tts.stop()
        tts.shutdown()
    }

    /** Picks the device-locale voice, falling back to English; false if neither has voice data. */
    private fun selectDefaultVoice(): Boolean {
        val primary = tts.setLanguage(Locale.getDefault())
        if (primary != TextToSpeech.LANG_MISSING_DATA && primary != TextToSpeech.LANG_NOT_SUPPORTED) return true
        val fallback = tts.setLanguage(Locale.ENGLISH)
        return fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun enqueue(text: String, segmentId: String) {
        val limit = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(1)
        val chunks = SpeechChunker.chunk(text, limit)
        val ids = progress.begin(segmentId, chunks.size)
        chunks.forEachIndexed { i, part ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val r = tts.speak(part, mode, null, ids[i])
            if (r != TextToSpeech.SUCCESS) Log.w(TAG, "chunk $i speak() returned $r")
        }
    }

    private companion object {
        const val TAG = "LunarisTTS"
    }
}
