package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.pages.SplitPlan
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel
import io.github.martindavinci.lunaris.pdf.ui.theme.currentRow

private val PanelPadding = 24.dp
private val Spacing = 12.dp
private val CardShape = RoundedCornerShape(16.dp)
private val CardPadding = 14.dp
private val RowLabelWidth = 52.dp
private val CountWidth = 72.dp
private val GoHeight = 52.dp
private val GoShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)
private val SelectedBorder = 2.dp
private val PlainBorder = 1.dp
private val RowsMaxHeight = 280.dp

/** One row for each new file, with its page count while typing. */
@Composable
internal fun SplitPanel(plan: SplitPlan, pageCount: Int, onSplit: () -> Unit, onAction: (PagesAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BottomPanel(onDismiss = { onAction(PagesAction.ClosePanel) }, modifier = Modifier.imePadding()) {
        Column(
            modifier = Modifier.padding(horizontal = PanelPadding).padding(bottom = PanelPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing),
        ) {
            Text(stringResource(R.string.pages_split_title), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                ModeCard(
                    icon = Icons.Filled.FormatListNumbered,
                    title = stringResource(R.string.pages_split_ranges),
                    detail = stringResource(R.string.pages_split_ranges_detail),
                    selected = !plan.everyPage,
                    modifier = Modifier.weight(1f),
                ) { onAction(PagesAction.SetSplitEveryPage(false)) }
                ModeCard(
                    icon = Icons.Filled.ViewAgenda,
                    title = stringResource(R.string.pages_split_every),
                    detail = pluralStringResource(R.plurals.pages_split_files, pageCount, pageCount),
                    selected = plan.everyPage,
                    modifier = Modifier.weight(1f),
                ) { onAction(PagesAction.SetSplitEveryPage(true)) }
            }
            if (!plan.everyPage) {
                Column(
                    modifier = Modifier.heightIn(max = RowsMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing),
                ) {
                    plan.rows.forEachIndexed { row, text -> RangeRow(row, text, plan.countOf(row, pageCount), onAction) }
                }
                TextButton(onClick = { onAction(PagesAction.AddSplitRow) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.pages_split_add_range), modifier = Modifier.padding(start = Spacing / 2))
                }
            }
            val files = plan.fileCount(pageCount)
            Text(pluralStringResource(R.plurals.pages_split_note, files, files), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Button(
                onClick = onSplit,
                enabled = plan.everyPage || plan.rows.indices.all { plan.countOf(it, pageCount) != null },
                shape = GoShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth().heightIn(min = GoHeight),
            ) {
                Icon(Icons.Filled.CallSplit, contentDescription = null)
                Text(pluralStringResource(R.plurals.pages_split_go, files, files), modifier = Modifier.padding(start = Spacing))
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, detail: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CardShape,
        color = if (selected) colors.currentRow else colors.surface,
        border = BorderStroke(if (selected) SelectedBorder else PlainBorder, if (selected) colors.primary else colors.outline),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing / 2)) {
            Icon(icon, contentDescription = null, tint = if (selected) colors.primary else colors.onSurface)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun RangeRow(row: Int, text: String, count: Int?, onAction: (PagesAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing / 2)) {
        Text(
            text = stringResource(R.string.pages_split_row, row + 1),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.widthIn(min = RowLabelWidth),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { onAction(PagesAction.SetSplitRow(row, it)) },
            singleLine = true,
            shape = FieldShape,
            isError = text.isNotBlank() && count == null,
            placeholder = { Text(stringResource(R.string.pages_split_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                count != null -> pluralStringResource(R.plurals.pages_split_count, count, count)
                text.isBlank() -> ""
                else -> stringResource(R.string.pages_split_wrong)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (count == null) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.width(CountWidth),
        )
        IconButton(onClick = { onAction(PagesAction.RemoveSplitRow(row)) }) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pages_split_remove_row))
        }
    }
}
