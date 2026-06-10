package tools.lunaris.texteditor.settings

import org.json.JSONObject

enum class AppTheme { LIGHT, DARK, BLACK }
enum class Suggestions { ON, OFF, OFF_AGGRESSIVE }

data class EditorSettings(
    val appTheme: AppTheme = AppTheme.DARK,
    val fullscreen: Boolean = false,
    val lineNumbers: Boolean = true,
    val wordWrap: Boolean = true,
    val autoIndent: Boolean = true,
    val fontSizeSp: Int = 16,
    val lineSpacingExtra: Int = 2,
    val monospace: Boolean = true,
    val sessionRestore: Boolean = false,
    val autosave: Boolean = false,
    val autosaveIntervalMinutes: Int = 1,
    val suggestions: Suggestions = Suggestions.OFF,
    val autoCaps: Boolean = false,
    val accessoryBarDefault: Boolean = false,
    val undoWithBack: Boolean = false,
    val defaultEncoding: String = "Auto",
    val defaultLineEnding: String = "Auto",
    val indentUnit: String = "\t",
    val folderDrawerEnabled: Boolean = true,
) {
    fun toJson(): String = JSONObject().apply {
        put("appTheme", appTheme.name); put("fullscreen", fullscreen)
        put("lineNumbers", lineNumbers); put("wordWrap", wordWrap); put("autoIndent", autoIndent)
        put("fontSizeSp", fontSizeSp); put("lineSpacingExtra", lineSpacingExtra); put("monospace", monospace)
        put("sessionRestore", sessionRestore); put("autosave", autosave); put("autosaveIntervalMinutes", autosaveIntervalMinutes)
        put("suggestions", suggestions.name); put("autoCaps", autoCaps); put("accessoryBarDefault", accessoryBarDefault)
        put("undoWithBack", undoWithBack); put("defaultEncoding", defaultEncoding); put("defaultLineEnding", defaultLineEnding)
        put("indentUnit", indentUnit); put("folderDrawerEnabled", folderDrawerEnabled)
    }.toString()

    companion object {
        private inline fun <reified E : Enum<E>> parseEnum(o: JSONObject, key: String, default: E): E {
            if (!o.has(key)) return default
            return runCatching { enumValueOf<E>(o.getString(key)) }.getOrNull() ?: default
        }
        fun fromJson(json: String): EditorSettings {
            if (json.isBlank()) return EditorSettings()
            val o = JSONObject(json)
            val d = EditorSettings()
            return EditorSettings(
                appTheme = parseEnum(o, "appTheme", d.appTheme),
                fullscreen = o.optBoolean("fullscreen", d.fullscreen),
                lineNumbers = o.optBoolean("lineNumbers", d.lineNumbers),
                wordWrap = o.optBoolean("wordWrap", d.wordWrap),
                autoIndent = o.optBoolean("autoIndent", d.autoIndent),
                fontSizeSp = o.optInt("fontSizeSp", d.fontSizeSp),
                lineSpacingExtra = o.optInt("lineSpacingExtra", d.lineSpacingExtra),
                monospace = o.optBoolean("monospace", d.monospace),
                sessionRestore = o.optBoolean("sessionRestore", d.sessionRestore),
                autosave = o.optBoolean("autosave", d.autosave),
                autosaveIntervalMinutes = o.optInt("autosaveIntervalMinutes", d.autosaveIntervalMinutes),
                suggestions = parseEnum(o, "suggestions", d.suggestions),
                autoCaps = o.optBoolean("autoCaps", d.autoCaps),
                accessoryBarDefault = o.optBoolean("accessoryBarDefault", d.accessoryBarDefault),
                undoWithBack = o.optBoolean("undoWithBack", d.undoWithBack),
                defaultEncoding = o.optString("defaultEncoding", d.defaultEncoding),
                defaultLineEnding = o.optString("defaultLineEnding", d.defaultLineEnding),
                indentUnit = o.optString("indentUnit", d.indentUnit),
                folderDrawerEnabled = o.optBoolean("folderDrawerEnabled", d.folderDrawerEnabled),
            )
        }
    }
}
