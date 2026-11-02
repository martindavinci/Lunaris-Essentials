package io.github.martindavinci.lunaris.scanner.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R

private val RowStartPadding = 16.dp
private val RowVerticalPadding = 8.dp
private val RowTextPadding = 16.dp

@Composable
internal fun HistoryRow(item: HistoryItem, nowMillis: Long, onOpen: () -> Unit, onDelete: () -> Unit) {
    val kindLabel = stringResource(item.kind.labelRes())
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = RowStartPadding, top = RowVerticalPadding, bottom = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.kind.icon(), contentDescription = kindLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = RowTextPadding).weight(1f)) {
            Text(
                text = item.summary.ifEmpty { kindLabel },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rowDetails(item, nowMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RowMenu(onDelete)
    }
}

@Composable
private fun rowDetails(item: HistoryItem, nowMillis: Long): String {
    val format = stringResource(item.code.format.labelRes())
    val date = relativeDate(item.timestampMillis, nowMillis) ?: return format
    return stringResource(R.string.history_row_details, format, date)
}

@Composable
private fun RowMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.history_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            )
        }
    }
}
