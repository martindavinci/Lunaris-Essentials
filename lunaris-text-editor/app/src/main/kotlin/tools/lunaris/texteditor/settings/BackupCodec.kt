package tools.lunaris.texteditor.settings

import org.json.JSONArray
import org.json.JSONObject
import tools.lunaris.texteditor.data.Associations
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.data.RecentFiles

data class Backup(
    val settings: EditorSettings,
    val associations: Map<String, String>,
    val colorSchemeId: String,
    val recents: List<RecentEntry>,
)

object BackupCodec {
    fun toJson(b: Backup): String = JSONObject().apply {
        put("settings", JSONObject(b.settings.toJson()))
        put("associations", JSONObject(Associations(b.associations).toJson()))
        put("colorSchemeId", b.colorSchemeId)
        put("recents", JSONArray(RecentFiles.toJson(b.recents)))
    }.toString()

    fun fromJson(json: String): Backup {
        if (json.isBlank()) return Backup(EditorSettings(), emptyMap(), "", emptyList())
        val o = JSONObject(json)
        return Backup(
            settings = EditorSettings.fromJson(o.optJSONObject("settings")?.toString() ?: ""),
            associations = Associations.fromJson(o.optJSONObject("associations")?.toString() ?: "").map,
            colorSchemeId = o.optString("colorSchemeId", ""),
            recents = RecentFiles.fromJson(o.optJSONArray("recents")?.toString() ?: ""),
        )
    }
}
