package tools.lunaris.texteditor.feature.preview

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.preview.HighlightAssets
import tools.lunaris.texteditor.preview.MarkdownRenderer
import tools.lunaris.texteditor.preview.PreviewHolder
import tools.lunaris.texteditor.preview.PreviewResolver
import tools.lunaris.texteditor.tts.DocumentSpeaker
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val holder: PreviewHolder,
    private val renderer: MarkdownRenderer,
    private val highlight: HighlightAssets,
    private val speaker: DocumentSpeaker,
) : ViewModel() {
    init {
        // Surface a message instead of failing silently when the device has no TTS engine/voice.
        speaker.onUnavailable = { _message.value = R.string.preview_tts_unavailable }
    }

    // Read the holder live (not snapshotted in init): as an overlay this VM is reused across
    // previews, so each render must reflect the latest requested document, not the first one.
    val title: String get() = holder.request?.title ?: ""

    /** Raw-HTML previews render the user's own page unsanitized, so JS must stay disabled for them. */
    val isRawHtmlPreview: Boolean
        get() = holder.request?.let { PreviewResolver.isRawHtml(it.fileName, it.languageHint) } ?: false

    /** Renders the document HTML for the chosen [dark]/light theme (null if nothing to preview). */
    fun html(dark: Boolean): String? =
        holder.request?.let {
            PreviewResolver.toHtmlDocument(
                it.fileName, it.text, renderer::toHtml, dark,
                highlightCss = if (dark) highlight.darkCss else highlight.lightCss,
                highlightJs = highlight.js,
                languageHint = it.languageHint,
            )
        }

    /** True while the document is being read aloud; drives the play/stop toggle icon. */
    val isSpeaking: StateFlow<Boolean> get() = speaker.isSpeaking

    // One-shot user message (string res), e.g. "no TTS voice available". Null once consumed.
    private val _message = MutableStateFlow<Int?>(null)
    @get:StringRes
    val message: StateFlow<Int?> = _message

    /** Clears the pending [message] after the UI has shown it. */
    fun messageShown() { _message.value = null }

    /** Starts reading the document aloud, or stops if already reading. */
    fun toggleSpeak() {
        if (speaker.isSpeaking.value) {
            speaker.stop()
            return
        }
        val req = holder.request ?: return
        speaker.speak(
            PreviewResolver.toSpeakableText(req.fileName, req.text, renderer::toPlainText, req.languageHint),
        )
    }

    /** Stops playback without tearing down the engine (e.g. when the screen leaves composition). */
    fun stopSpeaking() = speaker.stop()

    override fun onCleared() {
        speaker.shutdown()
    }
}
