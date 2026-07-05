package tools.lunaris.texteditor.feature.editor.action

import androidx.annotation.StringRes
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.feature.editor.EditorMenuAction
import tools.lunaris.texteditor.feature.editor.EditorMenuAction.*

/** Category headers in the action sheet. */
enum class ActionGroup { FILE, EDIT, TOOLS, VIEW }

@StringRes
fun ActionGroup.titleRes(): Int = when (this) {
    ActionGroup.FILE -> R.string.menu_group_file
    ActionGroup.EDIT -> R.string.menu_group_edit
    ActionGroup.TOOLS -> R.string.menu_group_tools
    ActionGroup.VIEW -> R.string.menu_group_view
}

/** One category + its ordered actions. */
data class ActionSection(val group: ActionGroup, val items: List<EditorMenuAction>)

/** Actions rendered as on/off tiles (tinted when active). */
val TOGGLE_ACTIONS: Set<EditorMenuAction> = setOf(WORD_WRAP, READ_ONLY, ACCESSORY_BAR)

/** Actions intentionally NOT in the sheet: nav-drawer destinations + toolbar-only undo/redo. */
val SHEET_EXCLUDED: Set<EditorMenuAction> = setOf(
    RECENT_FILES, FILE_ASSOCIATIONS, STORAGE_MANAGER, SETTINGS, UNDO, REDO,
)

/** The grouped catalog driving the action sheet grid + its search filter. */
val ACTION_SECTIONS: List<ActionSection> = listOf(
    ActionSection(
        ActionGroup.FILE,
        listOf(NEW_TAB, OPEN, OPEN_TREE, SAVE, SAVE_AS, RELOAD, SHARE, CLOSE),
    ),
    ActionSection(
        ActionGroup.EDIT,
        listOf(SELECT_ALL, PASTE, INSERT_COLOR, INSERT_TIMESTAMP, INDENT_INCREASE, INDENT_DECREASE, INPUT_METHOD),
    ),
    ActionSection(
        ActionGroup.TOOLS,
        listOf(SEARCH, GOTO_LINE, STATS, PREVIEW),
    ),
    ActionSection(
        ActionGroup.VIEW,
        listOf(SYNTAX, ENCODING, DISPLAY_STYLE, WORD_WRAP, READ_ONLY, ACCESSORY_BAR, TOOLBAR),
    ),
)
