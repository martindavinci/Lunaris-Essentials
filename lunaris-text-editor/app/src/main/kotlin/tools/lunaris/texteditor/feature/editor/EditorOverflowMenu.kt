package tools.lunaris.texteditor.feature.editor

import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Curated overflow menu: only [OVERFLOW_ITEMS], each with a leading icon. Toggle actions
 *  (word-wrap, read-only, accessory bar) show a trailing checkbox; [disabled] actions are
 *  greyed out (e.g. Share/Run with no content). */
@Composable
fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (EditorMenuAction) -> Unit,
    checked: Set<EditorMenuAction> = emptySet(),
    disabled: Set<EditorMenuAction> = emptySet(),
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        OVERFLOW_ITEMS.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(action.labelRes)) },
                leadingIcon = { Icon(action.icon(), contentDescription = null) },
                trailingIcon = if (action in OVERFLOW_TOGGLES) {
                    { Checkbox(checked = action in checked, onCheckedChange = null) }
                } else null,
                enabled = action !in disabled,
                onClick = { onDismiss(); onAction(action) },
            )
        }
    }
}
