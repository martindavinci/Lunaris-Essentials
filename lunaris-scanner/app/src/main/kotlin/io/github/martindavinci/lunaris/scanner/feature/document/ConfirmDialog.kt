package io.github.martindavinci.lunaris.scanner.feature.document

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.martindavinci.lunaris.scanner.R

@Composable
internal fun ConfirmDialog(confirm: DocumentConfirm, onAction: (DocumentAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(DocumentAction.Dismissed) },
        title = {
            Text(
                stringResource(
                    when (confirm) {
                        DocumentConfirm.DELETE_PAGE -> R.string.document_delete_title
                        DocumentConfirm.DISCARD -> R.string.document_discard_title
                    },
                ),
            )
        },
        text = if (confirm == DocumentConfirm.DISCARD) {
            { Text(stringResource(R.string.document_discard_message)) }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = { onAction(DocumentAction.Confirmed) }) {
                Text(
                    stringResource(
                        when (confirm) {
                            DocumentConfirm.DELETE_PAGE -> R.string.delete
                            DocumentConfirm.DISCARD -> R.string.document_discard
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DocumentAction.Dismissed) }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
