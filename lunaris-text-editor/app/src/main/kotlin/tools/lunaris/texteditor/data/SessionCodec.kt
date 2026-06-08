package tools.lunaris.texteditor.data

import org.json.JSONArray
import org.json.JSONObject

object SessionCodec {

    fun toJson(session: PersistedSession): String {
        val tabs = JSONArray()
        session.tabs.forEach { t ->
            tabs.put(
                JSONObject()
                    .put("id", t.id)
                    .put("uri", t.uri ?: JSONObject.NULL)
                    .put("title", t.title)
                    .put("text", t.text)
                    .put("dirty", t.dirty)
                    .put("charset", t.charsetName)
                    .put("eol", t.lineEnding)
                    .put("syntax", t.syntaxId ?: JSONObject.NULL),
            )
        }
        return JSONObject().put("active", session.activeId).put("tabs", tabs).toString()
    }

    fun fromJson(json: String): PersistedSession {
        if (json.isBlank()) return PersistedSession("", emptyList())
        val root = JSONObject(json)
        val arr = root.getJSONArray("tabs")
        val tabs = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PersistedTab(
                id = o.getString("id"),
                uri = if (o.isNull("uri")) null else o.getString("uri"),
                title = o.getString("title"),
                text = o.getString("text"),
                dirty = o.getBoolean("dirty"),
                charsetName = o.getString("charset"),
                lineEnding = o.getString("eol"),
                syntaxId = if (o.isNull("syntax")) null else o.getString("syntax"),
            )
        }
        return PersistedSession(root.optString("active", ""), tabs)
    }
}
