package tools.lunaris.texteditor.syntax

import javax.inject.Inject

/** Resolves a [SyntaxSelection] + filename to a TextMate scope name (null = plain text). */
class SyntaxResolver @Inject constructor(
    private val catalog: LanguageCatalog,
) {
    fun resolveScope(
        selection: SyntaxSelection,
        fileName: String?,
        associations: Map<String, String> = emptyMap(),
    ): String? = when (selection) {
        SyntaxSelection.None -> null
        is SyntaxSelection.Explicit -> catalog.findById(selection.languageId)?.scopeName
        SyntaxSelection.Auto -> fileName?.substringAfterLast('.', "")
            ?.takeIf { it.isNotEmpty() }
            ?.let { ext ->
                associations[ext.lowercase()]?.let { catalog.findById(it)?.scopeName }
                    ?: catalog.findByExtension(ext)?.scopeName
            }
    }
}
