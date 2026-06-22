package tools.lunaris.texteditor.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.edit.SearchOptions

@Composable
fun SearchDialog(
    onFind: (String, SearchOptions) -> Unit,
    onFindPrev: (String, SearchOptions) -> Unit,
    onReplace: (String, String, SearchOptions) -> Unit,
    onReplaceAll: (String, String, SearchOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(true) }
    fun opts() = SearchOptions(caseSensitive, regex, wrap)

    // Custom Dialog (not AlertDialog) so it can span nearly the full width — search/replace fields
    // are far more usable wide. Dismiss by tapping outside; no explicit cancel button.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.96f),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(stringResource(R.string.menu_search), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_query_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                OutlinedTextField(replacement, { replacement = it }, label = { Text(stringResource(R.string.search_replace_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                CheckRow(stringResource(R.string.search_case), caseSensitive) { caseSensitive = it }
                CheckRow(stringResource(R.string.search_regex), regex) { regex = it }
                CheckRow(stringResource(R.string.search_wrap), wrap) { wrap = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { onFindPrev(query, opts()) }) { Text(stringResource(R.string.search_find_prev)) }
                    TextButton(onClick = { onFind(query, opts()) }) { Text(stringResource(R.string.search_find)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { onReplace(query, replacement, opts()) }) { Text(stringResource(R.string.search_replace)) }
                    TextButton(onClick = { onReplaceAll(query, replacement, opts()) }) { Text(stringResource(R.string.search_replace_all)) }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
