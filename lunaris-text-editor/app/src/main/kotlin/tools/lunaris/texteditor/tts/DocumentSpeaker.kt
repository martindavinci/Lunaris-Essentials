package tools.lunaris.texteditor.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

/**
 * On-device text-to-speech for reading a document aloud. Wraps [TextToSpeech], whose init is
 * asynchronous: a [speak] issued before the engine is ready is queued and flushed once init
 * completes. If the device has no usable engine or voice, [onUnavailable] fires instead of
 * failing silently.
 *
 * Unscoped (no Hilt scope) so each [PreviewViewModel] gets its own instance; the owner MUST call
 * [shutdown] from `ViewModel.onCleared` to release the engine, or it leaks.
 */
class DocumentSpeaker @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Called on the main thread when a requested read-aloud can't play (no engine / no voice). */
    var onUnavailable: (() -> Unit)? = null

    private var settled = false          // init callback has fired
    private var usable = false           // engine initialized AND a voice is selectable
    private var pending: String? = null  // text requested before init settled
    private var lastUtteranceId: String? = null  // id of the final queued chunk (flips isSpeaking off)

    private val tts = TextToSpeech(context) { status ->
        settled = true
        usable = status == TextToSpeech.SUCCESS && selectVoice()
        val queued = pending
        pending = null
        when {
            usable && queued != null -> enqueue(queued)
            !usable && queued != null -> fail()
            else -> Unit
        }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
            // A document is spoken as several queued chunks; only the last one finishing ends playback.
            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtteranceId) _isSpeaking.value = false
            }
            // Required abstract override; deprecated in favour of the (id, errorCode) overload below.
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) { _isSpeaking.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "onError code=$errorCode")
                _isSpeaking.value = false
            }
        })
    }

    /** Speaks [text] from the start, replacing anything currently playing. No-op if blank. */
    fun speak(text: String) {
        if (text.isBlank()) return
        when {
            !settled -> { _isSpeaking.value = true; pending = text }  // still initializing
            usable -> { _isSpeaking.value = true; enqueue(text) }
            else -> fail()
        }
    }

    /** Picks the device locale's voice, falling back to English; false if neither has voice data. */
    private fun selectVoice(): Boolean {
        val primary = tts.setLanguage(Locale.getDefault())
        if (primary != TextToSpeech.LANG_MISSING_DATA && primary != TextToSpeech.LANG_NOT_SUPPORTED) return true
        val fallback = tts.setLanguage(Locale.ENGLISH)
        return fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun enqueue(text: String) {
        // speak() rejects any single string at/above the engine limit, so long docs are chunked and
        // queued: first chunk flushes, the rest append. isSpeaking clears when the last one finishes.
        val limit = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(1)
        val chunks = SpeechChunker.chunk(text, limit)
        lastUtteranceId = "$UTTERANCE_ID-${chunks.lastIndex}"
        chunks.forEachIndexed { i, part ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val r = tts.speak(part, mode, null, "$UTTERANCE_ID-$i")
            if (r != TextToSpeech.SUCCESS) Log.w(TAG, "chunk $i speak() returned $r")
        }
    }

    private fun fail() {
        _isSpeaking.value = false
        onUnavailable?.invoke()
    }

    /** Stops any in-progress or pending speech. */
    fun stop() {
        pending = null
        lastUtteranceId = null
        tts.stop()
        _isSpeaking.value = false
    }

    /** Releases the engine. The instance must not be reused afterwards. */
    fun shutdown() {
        pending = null
        lastUtteranceId = null
        onUnavailable = null
        tts.stop()
        tts.shutdown()
        _isSpeaking.value = false
    }

    private companion object {
        const val UTTERANCE_ID = "preview-read-aloud"
        const val TAG = "LunarisTTS"
    }
}
