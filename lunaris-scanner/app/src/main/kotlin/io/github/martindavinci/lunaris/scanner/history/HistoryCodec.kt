package io.github.martindavinci.lunaris.scanner.history

import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import org.json.JSONArray
import org.json.JSONObject

/** Stores the history as JSON. Reading never fails: whatever cannot be understood is left out. */
object HistoryCodec {
    private const val KEY_ID = "id"
    private const val KEY_TEXT = "text"
    private const val KEY_FORMAT = "format"
    private const val KEY_TIMESTAMP = "timestampMillis"
    private const val NO_ID = -1L
    private const val UNKNOWN_TIME = 0L

    fun toJson(entries: List<HistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(KEY_ID, entry.id)
                    .put(KEY_TEXT, entry.text)
                    .put(KEY_FORMAT, entry.format.name)
                    .put(KEY_TIMESTAMP, entry.timestampMillis),
            )
        }
        return array.toString()
    }

    fun fromJson(json: String): List<HistoryEntry> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index)?.toEntry() }
            .distinctBy { it.id }
    }

    private fun JSONObject.toEntry(): HistoryEntry? {
        val id = optLong(KEY_ID, NO_ID).takeIf { it != NO_ID } ?: return null
        val text = opt(KEY_TEXT) as? String ?: return null
        val formatName = optString(KEY_FORMAT)
        val format = CodeFormat.entries.firstOrNull { it.name == formatName } ?: return null
        return HistoryEntry(id, text, format, optLong(KEY_TIMESTAMP, UNKNOWN_TIME))
    }
}
