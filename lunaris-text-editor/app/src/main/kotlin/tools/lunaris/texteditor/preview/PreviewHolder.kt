package tools.lunaris.texteditor.preview

import javax.inject.Inject
import javax.inject.Singleton

/**
 * [languageHint] is the highlight.js language token for the doc's explicitly-chosen syntax, used
 * when [fileName] has no extension to derive it from (e.g. an untitled file set to C++).
 */
data class PreviewRequest(
    val title: String,
    val fileName: String?,
    val text: String,
    val languageHint: String? = null,
)

/** Carries the active document to the Preview destination (NavHost destinations get separate ViewModels). */
@Singleton
class PreviewHolder @Inject constructor() {
    @Volatile var request: PreviewRequest? = null
}
