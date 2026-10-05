package tools.lunaris.texteditor.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What the editor remembers about one file across sessions. */
data class FileMemory(
    /** Caret offset when the file was last left (closed, switched away from, app stopped). */
    val cursorOffset: Int? = null,
    /** Recent search queries in this file, newest first. */
    val searches: List<String> = emptyList(),
)

/** Per-file memory keyed by uri, persisted across app restarts. */
interface FileMemoryStore {
    fun memory(): Flow<Map<String, FileMemory>>
    suspend fun rememberCursor(uri: String, offset: Int)
    suspend fun rememberSearch(uri: String, query: String)
}

/**
 * Pure updates + JSON for [FileMemory] maps. Map iteration order is recency (oldest first), so
 * capping at [MAX_FILES] forgets the files least recently touched.
 */
object FileMemoryCodec {
    const val MAX_FILES = 200
    const val MAX_SEARCHES = 10

    fun withCursor(all: Map<String, FileMemory>, uri: String, offset: Int): Map<String, FileMemory> =
        touched(all, uri) { it.copy(cursorOffset = offset) }

    fun withSearch(all: Map<String, FileMemory>, uri: String, query: String): Map<String, FileMemory> {
        if (query.isBlank()) return all
        return touched(all, uri) { m -> m.copy(searches = (listOf(query) + (m.searches - query)).take(MAX_SEARCHES)) }
    }

    private fun touched(
        all: Map<String, FileMemory>,
        uri: String,
        change: (FileMemory) -> FileMemory,
    ): Map<String, FileMemory> {
        val updated = LinkedHashMap(all)
        val entry = change(updated.remove(uri) ?: FileMemory())
        updated[uri] = entry
        while (updated.size > MAX_FILES) updated.remove(updated.keys.first())
        return updated
    }

    fun toJson(all: Map<String, FileMemory>): String {
        val arr = JSONArray()
        all.forEach { (uri, m) ->
            val o = JSONObject().put(KEY_URI, uri).put(KEY_SEARCHES, JSONArray(m.searches))
            m.cursorOffset?.let { o.put(KEY_CURSOR, it) }
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(json: String): Map<String, FileMemory> {
        if (json.isBlank()) return emptyMap()
        return try {
            val arr = JSONArray(json)
            val out = LinkedHashMap<String, FileMemory>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val searches = o.optJSONArray(KEY_SEARCHES)
                out[o.getString(KEY_URI)] = FileMemory(
                    cursorOffset = if (o.has(KEY_CURSOR)) o.getInt(KEY_CURSOR) else null,
                    searches = searches?.let { s -> (0 until s.length()).map { s.getString(it) } }.orEmpty(),
                )
            }
            out
        } catch (_: JSONException) {
            emptyMap()   // a corrupt blob just means nothing is remembered
        }
    }

    private const val KEY_URI = "uri"
    private const val KEY_CURSOR = "cursor"
    private const val KEY_SEARCHES = "searches"
}
