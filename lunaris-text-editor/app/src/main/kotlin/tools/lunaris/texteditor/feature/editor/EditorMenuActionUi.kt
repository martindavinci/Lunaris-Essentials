package tools.lunaris.texteditor.feature.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated, ordered list of actions shown in the editor overflow menu (each with an icon).
 * File navigation (recents, bookmarks, associations, storage, settings) lives in the nav
 * drawer; frequent edit actions live on the bottom toolbar — so they are intentionally
 * not duplicated here.
 */
val OVERFLOW_ITEMS = listOf(
    EditorMenuAction.SEARCH,
    EditorMenuAction.SHARE,
    EditorMenuAction.GOTO_LINE,
    EditorMenuAction.SYNTAX,
    EditorMenuAction.ENCODING,
    EditorMenuAction.DISPLAY_STYLE,
    EditorMenuAction.STATS,
    EditorMenuAction.RUN,
    EditorMenuAction.WORD_WRAP,
    EditorMenuAction.READ_ONLY,
    EditorMenuAction.ACCESSORY_BAR,
)

/** Overflow actions rendered with a trailing checkbox (on/off state). */
val OVERFLOW_TOGGLES = setOf(
    EditorMenuAction.WORD_WRAP,
    EditorMenuAction.READ_ONLY,
    EditorMenuAction.ACCESSORY_BAR,
)

/** Leading icon for an overflow action (only [OVERFLOW_ITEMS] are rendered). */
fun EditorMenuAction.icon(): ImageVector = when (this) {
    EditorMenuAction.SEARCH -> Icons.Filled.Search
    EditorMenuAction.SHARE -> Icons.Filled.Share
    EditorMenuAction.GOTO_LINE -> Icons.Filled.FastForward
    EditorMenuAction.SYNTAX -> Icons.Filled.Edit
    EditorMenuAction.ENCODING -> Icons.Filled.Tag
    EditorMenuAction.DISPLAY_STYLE -> Icons.Filled.Palette
    EditorMenuAction.STATS -> Icons.Filled.BarChart
    EditorMenuAction.RUN -> Icons.Filled.PlayArrow
    EditorMenuAction.WORD_WRAP -> Icons.Filled.WrapText
    EditorMenuAction.READ_ONLY -> Icons.Filled.Lock
    EditorMenuAction.ACCESSORY_BAR -> Icons.Filled.Keyboard
    else -> Icons.Filled.Edit
}
