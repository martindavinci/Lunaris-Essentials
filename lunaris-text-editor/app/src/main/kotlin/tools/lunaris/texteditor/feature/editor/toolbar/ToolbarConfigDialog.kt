package tools.lunaris.texteditor.feature.editor.toolbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@Composable
fun ToolbarConfigDialog(
    initial: ToolbarConfig,
    onApply: (ToolbarConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var config by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onApply(config); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { config = ToolbarConfig.DEFAULT }) { Text(stringResource(R.string.toolbar_default)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
        title = { Text(stringResource(R.string.menu_toolbar)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                config.items.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item !in config.disabled,
                            onCheckedChange = { on -> config = config.toggled(item, on) },
                        )
                        Text(stringResource(item.labelRes()), modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (index > 0) config = config.moved(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.toolbar_move_up))
                        }
                        IconButton(onClick = { if (index < config.items.size - 1) config = config.moved(index, index + 1) }, enabled = index < config.items.size - 1) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.toolbar_move_down))
                        }
                    }
                }
            }
        },
    )
}
