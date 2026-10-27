package tools.lunaris.texteditor.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.PopupProperties
import tools.lunaris.texteditor.R

/**
 * The search query field. While it has focus, the file's recent queries (newest first) that match
 * what's typed float below it as hints; the dropdown overlays the dialog, so its layout never moves.
 */
@Composable
fun SearchQueryField(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    history: List<String>,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    // Keyed on the text only: caret/selection moves recompose the field but don't refilter.
    val hints = remember(history, query.text) { searchHints(history, query.text) }

    Box(modifier) {
        OutlinedTextField(
            query,
            { if (it.text != query.text) hidden = false; onQueryChange(it) },
            label = { Text(stringResource(R.string.search_query_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .onSizeChanged { fieldWidth = it.width }
                .onFocusChanged {
                    if (it.isFocused && !focused) hidden = false
                    focused = it.isFocused
                },
        )
        DropdownMenu(
            expanded = focused && !hidden && hints.isNotEmpty(),
            onDismissRequest = { hidden = true },
            // Not focusable: the keyboard keeps typing into the field while hints are shown.
            properties = PopupProperties(focusable = false),
            modifier = Modifier.width(with(LocalDensity.current) { fieldWidth.toDp() }),
        ) {
            hints.forEach { q ->
                DropdownMenuItem(
                    text = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                    onClick = {
                        onQueryChange(TextFieldValue(q, TextRange(q.length)))
                        hidden = true
                    },
                )
            }
        }
    }
}

/** Recents containing [query] (case-insensitive), minus the query itself; all of them when it's empty. */
internal fun searchHints(history: List<String>, query: String): List<String> =
    history.filter { it != query && it.contains(query, ignoreCase = true) }
