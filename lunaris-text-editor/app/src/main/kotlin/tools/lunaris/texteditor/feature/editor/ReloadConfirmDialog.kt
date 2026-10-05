package tools.lunaris.texteditor.feature.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tools.lunaris.texteditor.R

/** Asks before a reload (or reopen-with-encoding) replaces unsaved edits with the file on disk. */
@Composable
fun ReloadConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.reload_confirm_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.reload_confirm_title)) },
        text = { Text(stringResource(R.string.reload_confirm_message)) },
    )
}
