package tools.lunaris.texteditor.feature.preview

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.data.TtsPrefsStore
import tools.lunaris.texteditor.preview.HighlightAssets
import tools.lunaris.texteditor.preview.MarkdownRenderer
import tools.lunaris.texteditor.preview.PreviewHolder
import tools.lunaris.texteditor.preview.PreviewResolver
import tools.lunaris.texteditor.tts.DocumentReader
import tools.lunaris.texteditor.tts.DocumentSegmenter
import tools.lunaris.texteditor.tts.ReaderState
import tools.lunaris.texteditor.tts.Segment
import tools.lunaris.texteditor.tts.SegmentStyle
import tools.lunaris.texteditor.tts.SpeechEngine
import tools.lunaris.texteditor.tts.VoiceCatalog
import tools.lunaris.texteditor.tts.VoiceGroup
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val holder: PreviewHolder,
    private val renderer: MarkdownRenderer,
    private val highlight: HighlightAssets,
    private val reader: DocumentReader,
    private val engine: SpeechEngine,
    private val ttsPrefs: TtsPrefsStore,
) : ViewModel() {

    init { reader.onUnavailable = { _message.value = R.string.preview_tts_unavailable } }

    val title: String get() = holder.request?.title ?: ""

    val isRawHtmlPreview: Boolean
        get() = holder.request?.let { PreviewResolver.isRawHtml(it.fileName, it.languageHint) } ?: false

    fun html(dark: Boolean): String? =
        holder.request?.let {
            PreviewResolver.toHtmlDocument(
                it.fileName, it.text, renderer::toHtml, dark,
                highlightCss = if (dark) highlight.darkCss else highlight.lightCss,
                highlightJs = highlight.js,
                languageHint = it.languageHint,
            )
        }

    val readerState: StateFlow<ReaderState> get() = reader.state

    private val _readingText = MutableStateFlow("")
    val readingText: StateFlow<String> = _readingText

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments

    private val _voiceGroups = MutableStateFlow<List<VoiceGroup>>(emptyList())
    val voiceGroups: StateFlow<List<VoiceGroup>> = _voiceGroups

    private val _message = MutableStateFlow<Int?>(null)
    @get:StringRes
    val message: StateFlow<Int?> = _message
    fun messageShown() { _message.value = null }

    fun startReading(offlineLabel: String, networkLabel: String) {
        val req = holder.request ?: return
        val text = PreviewResolver.toSpeakableText(req.fileName, req.text, renderer::toPlainText, req.languageHint)
        val style = if (isProse(req.fileName, req.languageHint)) SegmentStyle.SENTENCE else SegmentStyle.LINE
        val segs = DocumentSegmenter.segment(text, style)
        _readingText.value = text
        _segments.value = segs
        _voiceGroups.value = VoiceCatalog.group(engine.voices(), offlineLabel, networkLabel)
        viewModelScope.launch {
            val rate = ttsPrefs.ttsRate().first()
            val voiceId = ttsPrefs.ttsVoiceId().first()
            reader.start(segs.map { it.text }, rate, voiceId)
        }
    }

    fun stopReading() = reader.stop()
    fun togglePause() = reader.togglePause()
    fun next() = reader.next()
    fun previous() = reader.previous()

    fun setRate(rate: Float) {
        reader.setRate(rate)
        viewModelScope.launch { ttsPrefs.setTtsRate(rate) }
    }

    fun setVoice(voiceId: String?) {
        reader.setVoice(voiceId)
        viewModelScope.launch { ttsPrefs.setTtsVoiceId(voiceId) }
    }

    override fun onCleared() {
        reader.stop()
        reader.shutdown()
    }

    /** Prose (sentence segmentation) for markdown + plain text; everything else is line-based. */
    private fun isProse(fileName: String?, languageHint: String?): Boolean {
        val ext = (fileName?.substringAfterLast('.', "")?.lowercase()?.ifEmpty { null }
            ?: languageHint?.lowercase()?.trim()).orEmpty()
        return ext in PROSE_EXTS
    }

    private companion object {
        val PROSE_EXTS = setOf("", "md", "markdown", "txt", "text", "log")
    }
}
