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

    fun findByExtension(ext: String): LanguageDefinition? = byExt[ext.lowercase()]
    fun findById(id: String): LanguageDefinition? = byId[id]

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
