package tools.lunaris.texteditor.preview

import javax.inject.Inject
import javax.inject.Singleton

data class PreviewRequest(val title: String, val fileName: String?, val text: String)

/** Carries the active document to the Preview destination (NavHost destinations get separate ViewModels). */
@Singleton
class PreviewHolder @Inject constructor() {
    @Volatile var request: PreviewRequest? = null
}
