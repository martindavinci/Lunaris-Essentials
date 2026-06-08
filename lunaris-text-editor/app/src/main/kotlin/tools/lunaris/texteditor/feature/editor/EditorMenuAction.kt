package tools.lunaris.texteditor.feature.editor

import androidx.annotation.StringRes
import tools.lunaris.texteditor.R

/** Items shown in the editor overflow menu. [labelRes] is the menu label. */
enum class EditorMenuAction(@StringRes val labelRes: Int) {
    SYNTAX(R.string.menu_syntax),
    DISPLAY_STYLE(R.string.menu_display_style),
    ENCODING(R.string.menu_encoding),
    RECENT_FILES(R.string.action_recent),
    SEARCH(R.string.menu_search),
    GOTO_LINE(R.string.menu_goto),
    STATS(R.string.menu_stats),
    INSERT_COLOR(R.string.menu_insert_color),
    INSERT_TIMESTAMP(R.string.menu_insert_timestamp),
    INDENT_INCREASE(R.string.menu_indent_increase),
    INDENT_DECREASE(R.string.menu_indent_decrease),
    UNDO(R.string.menu_undo),
    REDO(R.string.menu_redo),
    SELECT_ALL(R.string.menu_select_all),
    PASTE(R.string.menu_paste),
    WORD_WRAP(R.string.menu_word_wrap),
    READ_ONLY(R.string.menu_read_only),
    TOOLBAR(R.string.menu_toolbar),
}
