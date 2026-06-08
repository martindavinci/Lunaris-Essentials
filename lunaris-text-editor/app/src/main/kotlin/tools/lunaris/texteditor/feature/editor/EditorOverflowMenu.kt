package tools.lunaris.texteditor.feature.editor

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Overflow menu listing every [EditorMenuAction]; selecting one dismisses + dispatches it. */
@Composable
fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (EditorMenuAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        EditorMenuAction.entries.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(action.labelRes)) },
                onClick = { onDismiss(); onAction(action) },
            )
        }
    }
}
