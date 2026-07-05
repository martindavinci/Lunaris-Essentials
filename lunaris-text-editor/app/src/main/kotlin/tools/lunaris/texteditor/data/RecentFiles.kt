package tools.lunaris.texteditor.data

import org.json.JSONArray
import org.json.JSONObject

object RecentFiles {
    const val MAX = 50

    fun touch(list: List<RecentEntry>, entry: RecentEntry): List<RecentEntry> =
        (listOf(entry) + list.filterNot { it.uri == entry.uri }).take(MAX)

    fun remove(list: List<RecentEntry>, uri: String): List<RecentEntry> =
        list.filterNot { it.uri == uri }

    fun toJson(list: List<RecentEntry>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("timestamp", it.timestamp)) }
        return arr.toString()
    }

    fun fromJson(json: String): List<RecentEntry> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RecentEntry(o.getString("uri"), o.getString("name"), o.optLong("timestamp", 0L))
        }
    }
}
