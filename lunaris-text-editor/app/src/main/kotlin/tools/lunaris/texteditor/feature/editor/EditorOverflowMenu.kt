package tools.lunaris.texteditor.feature.editor

import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Generic dropdown rendering a list of [EditorMenuAction]s, each with a leading icon.
 *  Actions in [OVERFLOW_TOGGLES] show a trailing checkbox; [disabled] actions are greyed. */
@Composable
fun ActionDropdownMenu(
    items: List<EditorMenuAction>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (EditorMenuAction) -> Unit,
    checked: Set<EditorMenuAction> = emptySet(),
    disabled: Set<EditorMenuAction> = emptySet(),
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items.forEach { action ->
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

/** The overflow (⋮) menu. */
@Composable
fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (EditorMenuAction) -> Unit,
    checked: Set<EditorMenuAction> = emptySet(),
    disabled: Set<EditorMenuAction> = emptySet(),
) = ActionDropdownMenu(OVERFLOW_ITEMS, expanded, onDismiss, onAction, checked, disabled)
