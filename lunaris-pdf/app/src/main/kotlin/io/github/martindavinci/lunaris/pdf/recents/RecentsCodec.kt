package io.github.martindavinci.lunaris.pdf.recents

import io.github.martindavinci.lunaris.pdf.model.OpenDocument
import org.json.JSONArray
import org.json.JSONObject

/** Stores the recents as JSON. Reading never fails: whatever cannot be understood is left out. */
object RecentsCodec {
    private const val KEY_URI = "uri"
    private const val KEY_NAME = "name"
    private const val KEY_PAGE = "page"
    private const val KEY_SCALE = "scale"
    private const val KEY_LAST_OPENED = "lastOpenedMillis"

    fun toJson(entries: List<RecentEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(KEY_URI, entry.uri)
                    .put(KEY_NAME, entry.name)
                    .put(KEY_PAGE, entry.page)
                    .put(KEY_SCALE, entry.scale)
                    .put(KEY_LAST_OPENED, entry.lastOpenedMillis),
            )
        }
        return array.toString()
    }

    fun fromJson(json: String): List<RecentEntry> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index)?.toEntry() }
            .distinctBy { it.uri }
    }

    private fun JSONObject.toEntry(): RecentEntry? {
        val uri = opt(KEY_URI) as? String ?: return null
        return RecentEntry(
            uri = uri,
            name = opt(KEY_NAME) as? String ?: "",
            page = optInt(KEY_PAGE, 1).coerceAtLeast(1),
            scale = opt(KEY_SCALE) as? String ?: OpenDocument.DEFAULT_SCALE,
            lastOpenedMillis = optLong(KEY_LAST_OPENED, 0),
        )
    }
}
