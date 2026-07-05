package tools.lunaris.texteditor.feature.editor.action

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tools.lunaris.texteditor.feature.editor.EditorMenuAction

class ActionCatalogTest {

    private val sheetItems = ACTION_SECTIONS.flatMap { it.items }

    @Test fun everySheetActionAppearsExactlyOnce() {
        assertThat(sheetItems).containsNoDuplicates()
    }

    @Test fun sheetCoversEveryActionExceptExcluded() {
        val expected = EditorMenuAction.entries.toSet() - SHEET_EXCLUDED
        assertThat(sheetItems.toSet()).isEqualTo(expected)
    }

    @Test fun excludedAreDrawerNavAndToolbarActions() {
        assertThat(SHEET_EXCLUDED).containsExactly(
            EditorMenuAction.RECENT_FILES,
            EditorMenuAction.FILE_ASSOCIATIONS,
            EditorMenuAction.STORAGE_MANAGER,
            EditorMenuAction.SETTINGS,
            EditorMenuAction.UNDO,
            EditorMenuAction.REDO,
        )
    }

    @Test fun previewIsUnderTools() {
        val tools = ACTION_SECTIONS.first { it.group == ActionGroup.TOOLS }.items
        assertThat(tools).contains(EditorMenuAction.PREVIEW)
    }

    @Test fun toggleActionsAreAllInViewGroup() {
        val view = ACTION_SECTIONS.first { it.group == ActionGroup.VIEW }.items
        assertThat(view).containsAtLeastElementsIn(TOGGLE_ACTIONS)
    }
}
