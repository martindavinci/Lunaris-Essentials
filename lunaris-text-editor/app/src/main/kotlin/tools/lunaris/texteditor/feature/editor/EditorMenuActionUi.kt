package tools.lunaris.texteditor.feature.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.ui.graphics.vector.ImageVector

/** Leading icon for a menu action. */
fun EditorMenuAction.icon(): ImageVector = when (this) {
    EditorMenuAction.NEW_TAB -> Icons.Filled.Add
    EditorMenuAction.OPEN -> Icons.Filled.FolderOpen
    EditorMenuAction.OPEN_TREE -> Icons.Filled.Folder
    EditorMenuAction.SAVE -> Icons.Filled.Save
    EditorMenuAction.SAVE_AS -> Icons.Filled.SaveAs
    EditorMenuAction.RELOAD -> Icons.Filled.Refresh
    EditorMenuAction.CLOSE -> Icons.Filled.Close
    EditorMenuAction.UNDO -> Icons.AutoMirrored.Filled.Undo
    EditorMenuAction.REDO -> Icons.AutoMirrored.Filled.Redo
    EditorMenuAction.SELECT_ALL -> Icons.Filled.SelectAll
    EditorMenuAction.CUT -> Icons.Filled.ContentCut
    EditorMenuAction.PASTE -> Icons.Filled.ContentPaste
    EditorMenuAction.INSERT_COLOR -> Icons.Filled.ColorLens
    EditorMenuAction.INSERT_TIMESTAMP -> Icons.Filled.Schedule
    EditorMenuAction.INDENT_INCREASE -> Icons.AutoMirrored.Filled.FormatIndentIncrease
    EditorMenuAction.INDENT_DECREASE -> Icons.AutoMirrored.Filled.FormatIndentDecrease
    EditorMenuAction.INPUT_METHOD -> Icons.Filled.Keyboard
    EditorMenuAction.SEARCH -> Icons.Filled.Search
    EditorMenuAction.SHARE -> Icons.Filled.Share
    EditorMenuAction.GOTO_LINE -> Icons.Filled.FastForward
    EditorMenuAction.SYNTAX -> Icons.Filled.Edit
    EditorMenuAction.ENCODING -> Icons.Filled.Tag
    EditorMenuAction.DISPLAY_STYLE -> Icons.Filled.Palette
    EditorMenuAction.STATS -> Icons.Filled.BarChart
    EditorMenuAction.PREVIEW -> Icons.Filled.Visibility
    EditorMenuAction.WORD_WRAP -> Icons.Filled.WrapText
    EditorMenuAction.READ_ONLY -> Icons.Filled.Lock
    EditorMenuAction.ACCESSORY_BAR -> Icons.Filled.Keyboard
    else -> Icons.Filled.Edit
}
