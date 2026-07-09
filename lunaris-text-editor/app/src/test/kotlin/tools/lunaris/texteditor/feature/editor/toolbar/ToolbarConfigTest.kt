package tools.lunaris.texteditor.feature.editor.toolbar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolbarConfigTest {
    @Test fun default_containsAllItems_enablesSimplifiedBar() {
        val c = ToolbarConfig.DEFAULT
        // Every item is still present (config dialog can re-enable any; fromJson append still works).
        assertThat(c.items).containsExactlyElementsIn(ToolbarItem.entries)
        // Only the segmented default bar is enabled, in this order (select mode is off by default;
        // open+close form the file group just before preview; search+save trail as overflow-only).
        assertThat(c.enabled).containsExactly(
            ToolbarItem.CURSOR_LEFT, ToolbarItem.CURSOR_RIGHT,
            ToolbarItem.ACCESSORY,
            ToolbarItem.UNDO, ToolbarItem.REDO,
            ToolbarItem.OPEN, ToolbarItem.CLOSE, ToolbarItem.PREVIEW,
            ToolbarItem.SEARCH, ToolbarItem.SAVE,
        ).inOrder()
    }
    @Test fun toggle_disablesAndEnables() {
        val c = ToolbarConfig.DEFAULT.toggled(ToolbarItem.PASTE, false)
        assertThat(c.enabled).doesNotContain(ToolbarItem.PASTE)
        assertThat(c.toggled(ToolbarItem.PASTE, true).enabled).contains(ToolbarItem.PASTE)
    }
    @Test fun move_reordersItems() {
        val first = ToolbarConfig.DEFAULT.items.first()
        val c = ToolbarConfig.DEFAULT.moved(from = 0, to = 2)
        assertThat(c.items[2]).isEqualTo(first)
    }
    @Test fun jsonRoundTrip() {
        val c = ToolbarConfig.DEFAULT.toggled(ToolbarItem.UNDO, false).moved(0, 3)
        assertThat(ToolbarConfig.fromJson(c.toJson())).isEqualTo(c)
    }
    @Test fun fromJson_blank_isDefault() {
        assertThat(ToolbarConfig.fromJson("")).isEqualTo(ToolbarConfig.DEFAULT)
    }
    @Test fun fromJson_migratesLegacyRunNameToPreview() {
        // Pre-rename configs stored "RUN"; it must map onto PREVIEW (not be dropped),
        // preserving whether the user had it disabled.
        val legacy = """{"items":["CURSOR_LEFT","RUN","UNDO"],"disabled":["RUN"]}"""
        val c = ToolbarConfig.fromJson(legacy)
        assertThat(c.items).contains(ToolbarItem.PREVIEW)
        assertThat(c.enabled).doesNotContain(ToolbarItem.PREVIEW)
    }

    @Test fun fromJson_appendsNewlyAddedItems() {
        // a stored config missing some items still ends up containing every ToolbarItem
        val partial = """{"items":["OPEN","SAVE"],"disabled":[]}"""
        assertThat(ToolbarConfig.fromJson(partial).items).containsExactlyElementsIn(ToolbarItem.entries)
    }
}
