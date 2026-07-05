package tools.lunaris.texteditor.feature.editor.toolbar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolbarConfigTest {
    @Test fun default_containsAllItems_enablesSimplifiedBar() {
        val c = ToolbarConfig.DEFAULT
        // Every item is still present (config dialog can re-enable any; fromJson append still works).
        assertThat(c.items).containsExactlyElementsIn(ToolbarItem.entries)
        // Only the simplified default bar is enabled, in this order.
        assertThat(c.enabled).containsExactly(
            ToolbarItem.CURSOR_LEFT, ToolbarItem.CURSOR_RIGHT,
            ToolbarItem.UNDO, ToolbarItem.REDO, ToolbarItem.PREVIEW,
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
    @Test fun fromJson_appendsNewlyAddedItems() {
        // a stored config missing some items still ends up containing every ToolbarItem
        val partial = """{"items":["OPEN","SAVE"],"disabled":[]}"""
        assertThat(ToolbarConfig.fromJson(partial).items).containsExactlyElementsIn(ToolbarItem.entries)
    }
}
