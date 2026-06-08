package tools.lunaris.texteditor.feature.editor.toolbar

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import tools.lunaris.texteditor.R

@StringRes
fun ToolbarItem.labelRes(): Int = when (this) {
    ToolbarItem.OPEN -> R.string.toolbar_open
    ToolbarItem.PASTE -> R.string.toolbar_paste
    ToolbarItem.UNDO -> R.string.toolbar_undo
    ToolbarItem.REDO -> R.string.toolbar_redo
    ToolbarItem.SEARCH -> R.string.toolbar_search
    ToolbarItem.CURSOR_LEFT -> R.string.toolbar_cursor_left
    ToolbarItem.CURSOR_RIGHT -> R.string.toolbar_cursor_right
    ToolbarItem.SAVE -> R.string.toolbar_save
    ToolbarItem.CLOSE -> R.string.toolbar_close
}

fun ToolbarItem.icon(): ImageVector = when (this) {
    ToolbarItem.OPEN -> Icons.Filled.FolderOpen
    ToolbarItem.PASTE -> Icons.Filled.ContentPaste
    ToolbarItem.UNDO -> Icons.AutoMirrored.Filled.Undo
    ToolbarItem.REDO -> Icons.AutoMirrored.Filled.Redo
    ToolbarItem.SEARCH -> Icons.Filled.Search
    ToolbarItem.CURSOR_LEFT -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
    ToolbarItem.CURSOR_RIGHT -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    ToolbarItem.SAVE -> Icons.Filled.Save
    ToolbarItem.CLOSE -> Icons.Filled.Close
}
