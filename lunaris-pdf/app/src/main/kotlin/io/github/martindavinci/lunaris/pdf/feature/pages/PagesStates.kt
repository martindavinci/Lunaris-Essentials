package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.ui.theme.iconTile

private val CellMinWidth = 96.dp
private val EdgePadding = 16.dp
private val Spacing = 12.dp
private val InfoIcon = 18.dp
private val CardShape = RoundedCornerShape(18.dp)
private val TileSize = 40.dp
private val TileShape = RoundedCornerShape(12.dp)
private val BigTile = 72.dp
private val BigTileShape = RoundedCornerShape(20.dp)
private val ButtonHeight = 52.dp
private val ButtonShape = RoundedCornerShape(16.dp)
private val MessagePadding = 32.dp

/** Scrolls away with the grid: worth saying, not worth keeping in the way. */
@Composable
internal fun GridHeader(state: PagesUiState, dragging: Boolean) {
    val colors = MaterialTheme.colorScheme
    when {
        state.problem == PagesProblem.PROTECTED -> Surface(shape = CardShape, color = colors.surface, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing)) {
            Row(Modifier.padding(EdgePadding), horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                Box(Modifier.size(TileSize).clip(TileShape).background(colors.iconTile), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.primary)
                }
                Column {
                    Text(stringResource(R.string.pages_protected_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.pages_protected_message), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        // Kept in its place, unseen: the pages under the finger must not jump when a drag starts.
        else -> Row(Modifier.padding(bottom = Spacing).alpha(if (dragging) 0f else 1f), horizontalArrangement = Arrangement.spacedBy(Spacing)) {
            Icon(if (state.loading) Icons.Filled.HourglassTop else Icons.Filled.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(InfoIcon))
            Text(
                text = stringResource(if (state.loading) R.string.pages_loading else R.string.pages_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Damaged(state: PagesUiState, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        PagesTopBar(state, state.name, onBack = onBack, onSave = {}, onAction = {})
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(MessagePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EdgePadding, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(BigTile).clip(BigTileShape).background(colors.errorContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = colors.onErrorContainer)
            }
            Text(stringResource(R.string.pages_damaged_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.pages_damaged_message), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(
                onClick = onBack,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight),
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Text(stringResource(R.string.pages_back_to_reading), modifier = Modifier.padding(start = Spacing))
            }
        }
    }
}

/** The pages of another document, in the same grid: a tap chooses or leaves out. */
@Composable
internal fun PickPages(pick: PagePick, thumbnail: suspend (PagePick, Int) -> ImageBitmap?, onAction: (PagesAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Surface(color = colors.background) {
            Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(PagesAction.CancelPick) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.pages_pick_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(PagesAction.PickAll) }) { Text(stringResource(R.string.pages_select_all)) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CellMinWidth),
            contentPadding = PaddingValues(EdgePadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing),
            verticalArrangement = Arrangement.spacedBy(Spacing),
            modifier = Modifier.weight(1f),
        ) {
            items(pick.pageCount) { index ->
                val label = stringResource(R.string.pages_page_label, index + 1)
                PageTile(
                    number = index + 1,
                    turn = 0,
                    blankAspect = null,
                    selected = index in pick.chosen,
                    selecting = true,
                    dimmed = false,
                    key = pick.source to index,
                    load = { thumbnail(pick, index) },
                    modifier = Modifier
                        .clickable { onAction(PagesAction.TogglePicked(index)) }
                        .clearAndSetSemantics {
                            contentDescription = label
                            selected = index in pick.chosen
                        },
                )
            }
        }
        Surface(color = colors.background) {
            Button(
                onClick = { onAction(PagesAction.ConfirmPick) },
                enabled = pick.chosen.isNotEmpty(),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing).heightIn(min = ButtonHeight),
            ) {
                Text(pluralStringResource(R.plurals.pages_pick_add, pick.chosen.size, pick.chosen.size))
            }
        }
    }
}
