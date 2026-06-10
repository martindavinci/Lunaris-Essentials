package tools.lunaris.texteditor.feature.preview

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import tools.lunaris.texteditor.preview.MarkdownRenderer
import tools.lunaris.texteditor.preview.PreviewHolder
import tools.lunaris.texteditor.preview.PreviewResolver
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    holder: PreviewHolder,
    renderer: MarkdownRenderer,
) : ViewModel() {
    private val request = holder.request
    val title: String = request?.title ?: ""
    val html: String? = request?.let { PreviewResolver.toHtmlDocument(it.fileName, it.text, renderer::toHtml) }
}
