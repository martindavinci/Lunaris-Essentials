package tools.lunaris.texteditor.feature.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.data.RecentEntry
import tools.lunaris.texteditor.edit.RelativeLabel
import tools.lunaris.texteditor.edit.RelativeTime
import java.text.DateFormat
import java.util.Date

@Composable
fun RecentsPanel(recents: List<RecentEntry>, currentUri: String?, nowMillis: Long, onOpen: (String) -> Unit, onNewTab: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.drawer_recents), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        if (recents.isEmpty()) {
            Text(stringResource(R.string.recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(recents, key = { it.uri }) { e ->
                val bg = if (e.uri == currentUri) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
                Row(
                    Modifier.fillMaxWidth().background(bg).clickable { onOpen(e.uri) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(fileTypeIcon(e.name), contentDescription = null)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        relativeLabel(e.timestamp, nowMillis)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Button(onClick = onNewTab, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.drawer_new_file), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun relativeLabel(timestamp: Long, now: Long): String? = when (val l = RelativeTime.of(timestamp, now)) {
    RelativeLabel.Now -> stringResource(R.string.time_now)
    is RelativeLabel.Minutes -> stringResource(R.string.time_minutes, l.n)
    is RelativeLabel.Hours -> stringResource(R.string.time_hours, l.n)
    RelativeLabel.Yesterday -> stringResource(R.string.time_yesterday)
    is RelativeLabel.Days -> stringResource(R.string.time_days, l.n)
    is RelativeLabel.Older -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(l.millis))
    null -> null
}
