package tools.lunaris.texteditor.syntax

import org.json.JSONArray
import org.json.JSONObject

class LanguageCatalog(languages: List<LanguageDefinition>) {

    val languages: List<LanguageDefinition> = languages.toList()

    // If two languages declare the same extension, the last one in the list wins.
    private val byExt: Map<String, LanguageDefinition> =
        buildMap {
            languages.forEach { def -> def.extensions.forEach { put(it.lowercase(), def) } }
        }
    private val byId: Map<String, LanguageDefinition> = languages.associateBy { it.id }
    // Last language wins on a duplicate scope; a null scope maps to the plain-text entry.
    private val byScope: Map<String?, LanguageDefinition> = languages.associateBy { it.scopeName }

    fun findByExtension(ext: String): LanguageDefinition? = byExt[ext.lowercase()]
    fun findById(id: String): LanguageDefinition? = byId[id]

    /** Language whose grammar scope matches [scope]; a `null` scope maps to plain text. */
    fun findByScope(scope: String?): LanguageDefinition? = byScope[scope]

    /**
     * Display name for the editor's current syntax, or `null` when the resolved language is
     * plain text (scopeName == null) so callers can substitute a localized "Plain text".
     */
    fun displayName(syntax: SyntaxSelection, scopeName: String?): String? {
        val def = when (syntax) {
            is SyntaxSelection.Explicit -> findById(syntax.languageId)
            else -> findByScope(scopeName)
        }
        return def?.takeIf { it.scopeName != null }?.display
    }

    companion object {
        fun fromJson(json: String): LanguageCatalog {
            val arr = JSONObject(json).getJSONArray("languages")
            val defs = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LanguageDefinition(
                    id = o.getString("id"),
                    display = o.getString("display"),
                    scopeName = if (o.isNull("scopeName")) null else o.getString("scopeName"),
                    grammarAsset = if (o.isNull("grammarAsset")) null else o.getString("grammarAsset"),
                    extensions = parseExtensions(o.get("ext")),
                )
            }
            return LanguageCatalog(defs)
        }

        // "ext" may be a single string ("cs") or an array (["yml","yaml"]); accept both.
        private fun parseExtensions(value: Any): List<String> = when (value) {
            is JSONArray -> (0 until value.length()).map { value.getString(it) }
            else -> listOf(value.toString())
        }
    }
}
