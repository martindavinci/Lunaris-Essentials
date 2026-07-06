package tools.lunaris.texteditor.feature.editor.toolbar

import org.json.JSONArray
import org.json.JSONObject

/** Ordered toolbar items + which are disabled. */
data class ToolbarConfig(val items: List<ToolbarItem>, val disabled: Set<ToolbarItem>) {
    val enabled: List<ToolbarItem> get() = items.filter { it !in disabled }

    fun toggled(item: ToolbarItem, on: Boolean): ToolbarConfig =
        copy(disabled = if (on) disabled - item else disabled + item)

    fun moved(from: Int, to: Int): ToolbarConfig {
        val list = items.toMutableList()
        val x = list.removeAt(from)
        list.add(to, x)
        return copy(items = list)
    }

    fun toJson(): String {
        val items = JSONArray().also { a -> this.items.forEach { a.put(it.name) } }
        val dis = JSONArray().also { a -> disabled.forEach { a.put(it.name) } }
        return JSONObject().put("items", items).put("disabled", dis).toString()
    }

    companion object {
        // Segmented default: [cursor ◀ ▶ + select] · [# special chars] · [undo redo] ·
        // [gold open + red close] · preview. The rest stay present-but-disabled so users can add
        // them via the config dialog.
        private val DEFAULT_ORDER = listOf(
            ToolbarItem.CURSOR_LEFT, ToolbarItem.CURSOR_RIGHT, ToolbarItem.SELECT_MODE,
            ToolbarItem.ACCESSORY,
            ToolbarItem.UNDO, ToolbarItem.REDO,
            ToolbarItem.OPEN, ToolbarItem.CLOSE,
            ToolbarItem.PREVIEW,
            ToolbarItem.PASTE, ToolbarItem.SEARCH, ToolbarItem.SAVE,
        )
        // Select mode is present-but-disabled by default: available to add via the config dialog,
        // but off the default bar.
        private val DEFAULT_ENABLED = setOf(
            ToolbarItem.CURSOR_LEFT, ToolbarItem.CURSOR_RIGHT,
            ToolbarItem.ACCESSORY,
            ToolbarItem.UNDO, ToolbarItem.REDO,
            ToolbarItem.OPEN, ToolbarItem.CLOSE, ToolbarItem.PREVIEW,
        )
        val DEFAULT = ToolbarConfig(
            items = DEFAULT_ORDER,
            disabled = ToolbarItem.entries.toSet() - DEFAULT_ENABLED,
        )

        // Renamed enum members whose old name may still sit in a persisted config.
        private val LEGACY_NAMES = mapOf("RUN" to "PREVIEW")

        private fun parseItem(name: String): ToolbarItem? =
            runCatching { ToolbarItem.valueOf(LEGACY_NAMES[name] ?: name) }.getOrNull()

        fun fromJson(json: String): ToolbarConfig {
            if (json.isBlank()) return DEFAULT
            val o = JSONObject(json)
            val items = o.getJSONArray("items").let { a ->
                (0 until a.length()).mapNotNull { parseItem(a.getString(it)) }
            }.ifEmpty { return DEFAULT }
            val disabled = o.getJSONArray("disabled").let { a ->
                (0 until a.length()).mapNotNull { parseItem(a.getString(it)) }
            }.toSet()
            // Append any newly-added enum members so the toolbar never silently drops new actions.
            val complete = items + ToolbarItem.entries.filter { it !in items }
            return ToolbarConfig(complete, disabled)
        }
    }
}
