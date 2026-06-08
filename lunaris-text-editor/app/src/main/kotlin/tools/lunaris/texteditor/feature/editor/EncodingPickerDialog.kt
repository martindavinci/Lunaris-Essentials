package tools.lunaris.texteditor.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.syntax.CharsetCatalog
import java.nio.charset.Charset

@Composable
fun EncodingPickerDialog(
    catalog: CharsetCatalog,
    current: Charset,
    onSelected: (Charset) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(current.name()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                catalog.entries.firstOrNull { it.charset.name() == choice }?.let { onSelected(it.charset) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.menu_encoding)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                catalog.entries.forEach { entry ->
                    val selected = choice == entry.charset.name()
                    Row(Modifier.fillMaxWidth().selectable(selected) { choice = entry.charset.name() }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { choice = entry.charset.name() })
                        Text(entry.display)
                    }
                }
            }
        },
    )
}
