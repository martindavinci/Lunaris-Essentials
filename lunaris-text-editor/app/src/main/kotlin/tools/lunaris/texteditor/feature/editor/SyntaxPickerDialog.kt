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
import tools.lunaris.texteditor.syntax.LanguageCatalog
import tools.lunaris.texteditor.syntax.SyntaxSelection

@Composable
fun SyntaxPickerDialog(
    catalog: LanguageCatalog,
    current: SyntaxSelection,
    onSelected: (SyntaxSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(current) }
    val autoLabel = stringResource(R.string.syntax_auto)
    val noneLabel = stringResource(R.string.syntax_none)
    val rows: List<Pair<String, SyntaxSelection>> = remember(catalog, autoLabel, noneLabel) {
        buildList {
            add(autoLabel to SyntaxSelection.Auto)
            add(noneLabel to SyntaxSelection.None)
            catalog.languages.filter { it.scopeName != null }
                .forEach { add(it.display to SyntaxSelection.Explicit(it.id)) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSelected(choice); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.menu_syntax)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                rows.forEach { (label, selection) ->
                    val selected = choice == selection
                    Row(Modifier.fillMaxWidth().selectable(selected) { choice = selection }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { choice = selection })
                        Text(label)
                    }
                }
            }
        },
    )
}
