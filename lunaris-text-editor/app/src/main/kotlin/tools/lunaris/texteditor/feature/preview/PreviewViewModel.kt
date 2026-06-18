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
    holder: PreviewHolder,
    private val renderer: MarkdownRenderer,
    private val highlight: HighlightAssets,
) : ViewModel() {
    private val request = holder.request
    val title: String = request?.title ?: ""

    /** Renders the document HTML for the chosen [dark]/light theme (null if nothing to preview). */
    fun html(dark: Boolean): String? =
        request?.let {
            PreviewResolver.toHtmlDocument(
                it.fileName, it.text, renderer::toHtml, dark,
                highlightCss = if (dark) highlight.darkCss else highlight.lightCss,
                highlightJs = highlight.js,
            )
        }
}
