package tools.lunaris.texteditor.data

import org.json.JSONArray
import org.json.JSONObject

object Bookmarks {
    fun add(list: List<Bookmark>, b: Bookmark): List<Bookmark> =
        if (list.any { it.uri == b.uri && it.line == b.line }) list else listOf(b) + list

    fun remove(list: List<Bookmark>, b: Bookmark): List<Bookmark> =
        list.filterNot { it.uri == b.uri && it.line == b.line }

    fun toJson(list: List<Bookmark>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("line", it.line)) }
        return arr.toString()
    }

    fun fromJson(json: String): List<Bookmark> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Bookmark(o.getString("uri"), o.getString("name"), o.getInt("line"))
        }
    }
}
