package tools.lunaris.texteditor.feature.editor.toolbar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolbarConfigTest {
    @Test fun default_hasAllItemsEnabledInOrder() {
        val c = ToolbarConfig.DEFAULT
        assertThat(c.items).containsExactlyElementsIn(ToolbarItem.entries).inOrder()
        assertThat(c.enabled).containsExactlyElementsIn(ToolbarItem.entries)
    }
    @Test fun toggle_disablesAndEnables() {
        val c = ToolbarConfig.DEFAULT.toggled(ToolbarItem.PASTE, false)
        assertThat(c.enabled).doesNotContain(ToolbarItem.PASTE)
        assertThat(c.toggled(ToolbarItem.PASTE, true).enabled).contains(ToolbarItem.PASTE)
    }
    @Test fun move_reordersItems() {
        val first = ToolbarItem.entries.first()
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
