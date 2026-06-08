package tools.lunaris.texteditor.syntax

import org.json.JSONObject

class LanguageCatalog(val languages: List<LanguageDefinition>) {

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
                    extensions = o.getJSONArray("ext").let { e -> (0 until e.length()).map { e.getString(it) } },
                )
            }
            return LanguageCatalog(defs)
        }
    }
}
