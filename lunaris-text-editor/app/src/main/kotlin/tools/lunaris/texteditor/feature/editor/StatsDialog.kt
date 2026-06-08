package tools.lunaris.texteditor.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tools.lunaris.texteditor.R

@Composable
fun StatsDialog(words: Int, characters: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text(stringResource(R.string.menu_stats)) },
        text = {
            Column {
                Text(stringResource(R.string.stats_words, words))
                Text(stringResource(R.string.stats_chars, characters))
            }
        },
    )
}
