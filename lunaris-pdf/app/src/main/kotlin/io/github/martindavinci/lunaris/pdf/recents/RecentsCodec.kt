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
    private const val KEY_PAGES = "pages"
    private const val KEY_CHAPTER = "chapter"
    private const val KEY_LOCKED = "locked"
    private const val KEY_PINNED = "pinned"

    fun toJson(entries: List<RecentEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(KEY_URI, entry.uri)
                    .put(KEY_NAME, entry.name)
                    .put(KEY_PAGE, entry.page)
                    .put(KEY_SCALE, entry.scale)
                    .put(KEY_LAST_OPENED, entry.lastOpenedMillis)
                    .put(KEY_PAGES, entry.pages)
                    .put(KEY_CHAPTER, entry.chapter)
                    .put(KEY_LOCKED, entry.locked)
                    .put(KEY_PINNED, entry.pinned),
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
            pages = optInt(KEY_PAGES, 0).coerceAtLeast(0),
            chapter = opt(KEY_CHAPTER) as? String ?: "",
            locked = optBoolean(KEY_LOCKED, false),
            pinned = optBoolean(KEY_PINNED, false),
        )
    }
}
