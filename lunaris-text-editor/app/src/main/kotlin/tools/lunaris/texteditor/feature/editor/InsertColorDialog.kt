package tools.lunaris.texteditor.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R

private val PRESET_COLORS = listOf(
    0xF44336, 0xE91E63, 0x9C27B0, 0x3F51B5, 0x2196F3, 0x009688,
    0x4CAF50, 0xFFEB3B, 0xFF9800, 0x795548, 0x000000, 0xFFFFFF,
)

@Composable
fun InsertColorDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.menu_insert_color)) },
        text = {
            Column {
                PRESET_COLORS.chunked(6).forEach { rowColors ->
                    Row {
                        rowColors.forEach { rgb ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(40.dp)
                                    .background(Color(0xFF000000.toInt() or rgb))
                                    .clickable { onPick(rgb); onDismiss() }
                            )
                        }
                    }
                }
            }
        },
    )
}
