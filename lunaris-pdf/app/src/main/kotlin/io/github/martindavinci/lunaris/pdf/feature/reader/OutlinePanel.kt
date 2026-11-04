package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel
import io.github.martindavinci.lunaris.pdf.ui.theme.currentRow
import io.github.martindavinci.lunaris.pdf.ui.theme.currentText
import io.github.martindavinci.lunaris.pdf.viewer.DocumentChapters
import io.github.martindavinci.lunaris.pdf.viewer.OutlineItem

private const val PANEL_HEIGHT_FRACTION = 0.8f
private const val MAX_PAGE_DIGITS = 7
private const val MAX_INDENT_LEVELS = 5
private const val ENTRIES_ABOVE_CURRENT = 2
private val IndentPerLevel = 20.dp
private val EdgePadding = 16.dp
private val RowHeight = 48.dp
private val RowShape = RoundedCornerShape(24.dp)
private val RowSpacing = 12.dp
private val GoHeight = 56.dp
private val HereShape = RoundedCornerShape(12.dp)
private val HerePaddingHorizontal = 8.dp
private val HerePaddingVertical = 2.dp

/** Go to page on top, then the contents, opened on the chapter being read. */
@Composable
internal fun OutlinePanel(
    items: List<OutlineItem>,
    page: Int,
    pages: Int,
    onSelect: (Int) -> Unit,
    onPages: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = remember(items, page) { DocumentChapters.currentIndex(items, page) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - ENTRIES_ABOVE_CURRENT).coerceAtLeast(0))
    BottomPanel(onDismiss = onDismiss, modifier = if (items.isEmpty()) Modifier else Modifier.fillMaxHeight(PANEL_HEIGHT_FRACTION)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = EdgePadding + EdgePadding / 2, end = EdgePadding / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_outline),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPages) { Text(stringResource(R.string.pages_title)) }
        }
        GoToPageRow(pages, onSelect)
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.reader_outline_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = EdgePadding + EdgePadding / 2, end = EdgePadding, bottom = EdgePadding * 2),
            )
        } else {
            LazyColumn(state = list, modifier = Modifier.padding(horizontal = EdgePadding / 2)) {
                itemsIndexed(items) { index, item ->
                    OutlineRow(item, isCurrent = index == current, page = page, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun GoToPageRow(pages: Int, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val go = { text.toIntOrNull()?.takeIf { it in 1..pages }?.let(onGo) ?: Unit }
    Row(
        modifier = Modifier.fillMaxWidth().padding(EdgePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { value -> text = value.filter { it.isDigit() }.take(MAX_PAGE_DIGITS) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.reader_go_to_page)) },
            suffix = { Text(stringResource(R.string.reader_of_pages, pages)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { go() },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.heightIn(min = GoHeight),
        ) {
            Text(stringResource(R.string.reader_go))
        }
    }
}

@Composable
private fun OutlineRow(item: OutlineItem, isCurrent: Boolean, page: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val target = item.page
    val nested = item.depth > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .then(if (isCurrent) Modifier.background(colors.currentRow, RowShape) else Modifier)
            .then(if (target != null) Modifier.clickable { onSelect(target) } else Modifier)
            .padding(start = EdgePadding + IndentPerLevel * item.depth.coerceAtMost(MAX_INDENT_LEVELS), end = EdgePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        Text(
            text = item.title,
            style = if (nested) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = when {
                isCurrent -> colors.currentText
                nested -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isCurrent) {
            Surface(shape = HereShape, color = colors.primaryContainer) {
                Text(
                    text = stringResource(R.string.reader_outline_here, page),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = HerePaddingHorizontal, vertical = HerePaddingVertical),
                )
            }
        } else if (target != null) {
            Text(
                text = target.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
