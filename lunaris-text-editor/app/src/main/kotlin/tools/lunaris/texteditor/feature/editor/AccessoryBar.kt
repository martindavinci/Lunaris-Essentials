package tools.lunaris.texteditor.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// insert-string to display-glyph. Single-char code symbols are language-neutral literals.
private val ACCESSORY_KEYS = listOf(
    "\t" to "⇥", "{" to "{", "}" to "}", "(" to "(", ")" to ")", "[" to "[", "]" to "]",
    "<" to "<", ">" to ">", ";" to ";", ":" to ":", "=" to "=", "\"" to "\"", "'" to "'",
    "/" to "/", "\\" to "\\", "|" to "|", "&" to "&", "#" to "#", "*" to "*",
)

@Composable
fun AccessoryBar(
    onKey: (String) -> Unit,
    onCursorLeft: () -> Unit,
    onCursorRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCursorLeft) { Text("◄") }
            ACCESSORY_KEYS.forEach { (insert, label) ->
                TextButton(onClick = { onKey(insert) }) { Text(label) }
            }
            TextButton(onClick = onCursorRight) { Text("►") }
        }
    }
}
