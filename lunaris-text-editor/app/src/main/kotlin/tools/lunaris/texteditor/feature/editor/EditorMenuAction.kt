package tools.lunaris.texteditor.feature.editor

import androidx.annotation.StringRes
import tools.lunaris.texteditor.R

/** Items shown in the editor overflow menu. [labelRes] is the menu label. */
enum class EditorMenuAction(@StringRes val labelRes: Int) {
    SYNTAX(R.string.menu_syntax),
    DISPLAY_STYLE(R.string.menu_display_style),
    ENCODING(R.string.menu_encoding),
    RECENT_FILES(R.string.action_recent),
}
