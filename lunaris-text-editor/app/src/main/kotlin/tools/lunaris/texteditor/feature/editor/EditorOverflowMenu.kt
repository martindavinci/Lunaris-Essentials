package tools.lunaris.texteditor.feature.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Overflow menu listing every [EditorMenuAction]; selecting one dismisses + dispatches it.
 *  Toggle actions (e.g. WORD_WRAP, READ_ONLY) show a trailing check icon when active. */
@Composable
fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (EditorMenuAction) -> Unit,
    checked: Set<EditorMenuAction> = emptySet(),
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        EditorMenuAction.entries.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(action.labelRes)) },
                trailingIcon = if (action in checked) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
                onClick = { onDismiss(); onAction(action) },
            )
        }
    }
}
