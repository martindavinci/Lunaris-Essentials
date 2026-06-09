package tools.lunaris.texteditor.data

import org.json.JSONObject

/** Immutable extension(lowercase) -> languageId map. */
data class Associations(val map: Map<String, String>) {
    val entries: List<Pair<String, String>> get() = map.entries.map { it.key to it.value }

    fun languageFor(extension: String): String? = map[extension.lowercase()]
    fun put(extension: String, languageId: String): Associations = copy(map = map + (extension.lowercase() to languageId))
    fun remove(extension: String): Associations = copy(map = map - extension.lowercase())

    fun toJson(): String {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    companion object {
        fun empty() = Associations(emptyMap())
        fun fromJson(json: String): Associations {
            if (json.isBlank()) return empty()
            val o = JSONObject(json)
            return Associations(o.keys().asSequence().associateWith { o.getString(it) })
        }
    }
}
