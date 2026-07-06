package tools.lunaris.texteditor.feature.preview

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import tools.lunaris.texteditor.preview.HighlightAssets
import tools.lunaris.texteditor.preview.MarkdownRenderer
import tools.lunaris.texteditor.preview.PreviewHolder
import tools.lunaris.texteditor.preview.PreviewResolver
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val holder: PreviewHolder,
    private val renderer: MarkdownRenderer,
    private val highlight: HighlightAssets,
) : ViewModel() {
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
}
