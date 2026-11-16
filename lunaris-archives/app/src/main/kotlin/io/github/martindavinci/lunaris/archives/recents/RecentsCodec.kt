package io.github.martindavinci.lunaris.archives.recents

import org.json.JSONArray
import org.json.JSONObject

/** The recents as JSON in the preferences. Reading never fails: a damaged value is an empty list. */
object RecentsCodec {
    private const val URI = "uri"
    private const val NAME = "name"
    private const val FORMAT = "format"
    private const val SIZE = "size"
    private const val FILES = "files"
    private const val LOCKED = "locked"
    private const val OPENED = "opened"
    private const val PATH = "path"

    fun encode(list: List<RecentArchive>): String = JSONArray().apply {
        list.forEach { item ->
            put(JSONObject().apply {
                put(URI, item.uri); put(NAME, item.name); put(FORMAT, item.formatLabel)
                item.sizeBytes?.let { put(SIZE, it) }
                put(FILES, item.fileCount); put(LOCKED, item.locked); put(OPENED, item.lastOpenedMillis)
                item.lastPath?.let { put(PATH, JSONArray(it)) }
            })
        }
    }.toString()

    fun decode(json: String): List<RecentArchive> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.let(::row) }
            .distinctBy { it.uri }
    }

    private fun row(o: JSONObject): RecentArchive? {
        val uri = o.optString(URI).takeIf { it.isNotEmpty() } ?: return null
        return RecentArchive(
            uri = uri,
            name = o.optString(NAME),
            formatLabel = o.optString(FORMAT),
            sizeBytes = if (o.has(SIZE)) o.optLong(SIZE).coerceAtLeast(0) else null,
            fileCount = o.optInt(FILES).coerceAtLeast(0),
            locked = o.optBoolean(LOCKED),
            lastOpenedMillis = o.optLong(OPENED).coerceAtLeast(0),
            lastPath = o.optJSONArray(PATH)?.let { path -> (0 until path.length()).map { path.optString(it) } },
        )
    }
}
