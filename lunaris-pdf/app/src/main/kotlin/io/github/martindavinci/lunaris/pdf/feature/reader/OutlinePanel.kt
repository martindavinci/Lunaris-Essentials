package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.viewer.OutlineItem

private const val PANEL_HEIGHT_FRACTION = 0.7f
private const val SCRIM_ALPHA = 0.5f
private const val MAX_INDENT_LEVELS = 5
private val IndentPerLevel = 16.dp
private val RowPadding = 16.dp

/** A panel over a scrim, closed by back or a tap outside. Material's bottom sheet is still an experimental API. */
@Composable
internal fun OutlinePanel(items: List<OutlineItem>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(PANEL_HEIGHT_FRACTION)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.reader_outline),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(RowPadding),
                )
                LazyColumn {
                    items(items) { item -> OutlineRow(item, onSelect) }
                }
            }
        }
    }
}

@Composable
private fun OutlineRow(item: OutlineItem, onSelect: (Int) -> Unit) {
    val page = item.page
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (page != null) Modifier.clickable { onSelect(page) } else Modifier)
            .padding(
                start = RowPadding + IndentPerLevel * item.depth.coerceAtMost(MAX_INDENT_LEVELS),
                end = RowPadding,
                top = RowPadding / 2,
                bottom = RowPadding / 2,
            ),
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (page != null) {
            Text(
                text = page.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = RowPadding),
            )
        }
    }
}
