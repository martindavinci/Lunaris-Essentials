package tools.lunaris.texteditor.feature.drawer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.feature.settings.FileAssociationViewModel

@Composable
fun ExtensionsPanel(viewModel: FileAssociationViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var pickerExt by remember { mutableStateOf<String?>(null) }
    var addPrompt by remember { mutableStateOf(false) }
    var removeExt by remember { mutableStateOf<String?>(null) }

    pickerExt?.let { ext ->
        LanguagePicker(ext, rows.firstOrNull { it.ext == ext }?.langId, viewModel.pickerLanguages,
            onPick = { viewModel.setLanguage(ext, it); pickerExt = null }, onBack = { pickerExt = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.assoc_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.assoc_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.ext }) { row ->
                Row(
                    Modifier.fillMaxWidth()
                        .pointerInput(row.ext) {
                            detectTapGestures(onTap = { pickerExt = row.ext }, onLongPress = { removeExt = row.ext })
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = RoundedCornerShape(percent = 40), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(".${row.ext}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    val faded = viewModel.pickerLanguages.firstOrNull { it.id == row.langId }?.scopeName == null
                    val label = if (faded) stringResource(R.string.assoc_plaintext) else row.display
                    Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp),
                        color = if (faded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
        Button(onClick = { addPrompt = true }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null); Text(stringResource(R.string.assoc_add), Modifier.padding(start = 8.dp))
        }
    }

    if (addPrompt) {
        NameInputDialog(title = stringResource(R.string.assoc_extension), confirmLabel = stringResource(R.string.ok),
            onConfirm = { ext -> addPrompt = false; pickerExt = ext.trim().removePrefix(".") }, onDismiss = { addPrompt = false })
    }
    removeExt?.let { ext ->
        AlertDialog(onDismissRequest = { removeExt = null },
            title = { Text(stringResource(R.string.assoc_remove_title)) },
            confirmButton = { TextButton(onClick = { viewModel.remove(ext); removeExt = null }) { Text(stringResource(R.string.assoc_remove)) } },
            dismissButton = { TextButton(onClick = { removeExt = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
