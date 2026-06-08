package tools.lunaris.texteditor.syntax

import org.json.JSONObject

class ColorSchemeCatalog(
    schemes: List<ColorSchemeDefinition>,
    requestedDefault: String,
) {
    val schemes: List<ColorSchemeDefinition> = schemes.toList()
    private val byId = this.schemes.associateBy { it.id }

    val defaultId: String =
        this.schemes.firstOrNull { it.id == requestedDefault || it.display == requestedDefault }?.id
            ?: this.schemes.firstOrNull()?.id
            ?: requestedDefault

    fun findById(id: String): ColorSchemeDefinition? = byId[id]
    fun defaultScheme(): ColorSchemeDefinition = findById(defaultId) ?: schemes.first()

    companion object {
        fun fromJson(json: String): ColorSchemeCatalog {
            val root = JSONObject(json)
            val arr = root.getJSONArray("themes")
            val defs = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ColorSchemeDefinition(
                    id = o.getString("id"),
                    display = o.getString("display"),
                    themeAsset = o.getString("themeAsset"),
                    dark = o.optBoolean("dark", true),
                )
            }
            return ColorSchemeCatalog(defs, root.optString("default", defs.firstOrNull()?.id ?: ""))
        }
    }
}
