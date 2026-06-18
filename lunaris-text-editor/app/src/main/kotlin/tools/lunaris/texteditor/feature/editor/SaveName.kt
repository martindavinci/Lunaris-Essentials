package tools.lunaris.texteditor.feature.editor

import tools.lunaris.texteditor.document.DocumentSession
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.SyntaxSelection

/**
 * Default name offered in the save picker. A file with a real name keeps it; an untitled file
 * gets "untitled.<ext>" where the extension follows the chosen syntax (e.g. Python -> .py,
 * C++ -> .cpp), defaulting to .txt when the syntax is Auto/None/unknown.
 */
fun suggestedSaveName(state: EditorUiState, catalog: LanguageCatalog): String {
    if (state.title != DocumentSession.UNTITLED) return state.title.trim()
    val ext = (state.syntax as? SyntaxSelection.Explicit)
        ?.let { sel -> catalog.languages.firstOrNull { it.id == sel.languageId }?.extensions?.firstOrNull() }
        ?: "txt"
    return "untitled.$ext"
}
