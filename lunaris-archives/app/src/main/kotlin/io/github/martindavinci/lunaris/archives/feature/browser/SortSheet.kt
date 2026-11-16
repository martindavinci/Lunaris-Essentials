package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.browse.SortField
import io.github.martindavinci.lunaris.archives.browse.SortOrder
import io.github.martindavinci.lunaris.archives.ui.BottomPanel
import io.github.martindavinci.lunaris.archives.ui.ChoiceRow
import io.github.martindavinci.lunaris.archives.ui.PanelHeader

private val RowHeight = 56.dp
private val RowPadding = 24.dp

/** Sort by: the field, folders first, and the direction in words; every change applies at once. */
@Composable
internal fun SortSheet(order: SortOrder, onAction: (BrowserAction) -> Unit) {
    val set = { next: SortOrder -> onAction(BrowserAction.SetSort(next)) }
    BottomPanel(onDismiss = { onAction(BrowserAction.DismissSheet) }) {
        PanelHeader(stringResource(R.string.sort_title), null)
        FIELDS.forEach { (field, label) ->
            ChoiceRow(stringResource(label), null, selected = order.field == field, onClick = { set(order.copy(field = field)) })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        val (up, down) = DIRECTIONS.getValue(order.field)
        ChoiceRow(stringResource(up), null, selected = order.ascending, onClick = { set(order.copy(ascending = true)) })
        ChoiceRow(stringResource(down), null, selected = !order.ascending, onClick = { set(order.copy(ascending = false)) })
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(
            Modifier.fillMaxWidth().heightIn(min = RowHeight)
                .toggleable(value = order.foldersFirst, role = Role.Switch, onValueChange = { set(order.copy(foldersFirst = it)) })
                .padding(horizontal = RowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.sort_folders_first), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = order.foldersFirst, onCheckedChange = null)
        }
    }
}

private val FIELDS = listOf(
    SortField.NAME to R.string.sort_name, SortField.SIZE to R.string.sort_size,
    SortField.DATE to R.string.sort_date, SortField.TYPE to R.string.sort_type,
)

private val DIRECTIONS = mapOf(
    SortField.NAME to (R.string.sort_ascending_name to R.string.sort_descending_name),
    SortField.TYPE to (R.string.sort_ascending_name to R.string.sort_descending_name),
    SortField.SIZE to (R.string.sort_smallest to R.string.sort_largest),
    SortField.DATE to (R.string.sort_oldest to R.string.sort_newest),
)
