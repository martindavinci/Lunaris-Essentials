package tools.lunaris.texteditor.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R

private val RowPadding = 8.dp
private val IconGap = 12.dp

/** The file's recent search queries (newest first); tapping one puts it back in the query. */
@Composable
fun RecentSearches(queries: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (queries.isEmpty()) return
    Column(modifier) {
        Text(
            stringResource(R.string.search_recent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = RowPadding),
        )
        queries.forEach { q ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(q) }.padding(vertical = RowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.History, contentDescription = null)
                Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = IconGap))
            }
        }
    }
}
