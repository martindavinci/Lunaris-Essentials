package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.ui.theme.iconTile
import kotlin.math.roundToInt

private const val DRAGGED_SCALE = 1.03f
private const val HEADER = "header"
private const val ADD = "add"
private const val LAYOUT = "layout"
private val EdgePadding = 16.dp
private val Spacing = 12.dp
private val BigButtonHeight = 52.dp
private val BigButtonShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(14.dp)
private val TileSize = 56.dp
private val TileShape = RoundedCornerShape(16.dp)
private val DashedHeight = 52.dp
private val DashedStroke = 1.dp
private val DragShadow = 8.dp
private val EmptyPadding = 32.dp

@Composable
internal fun EmptyList(onAddFiles: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(EmptyPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EdgePadding, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
            listOf(Icons.Filled.PictureAsPdf, Icons.Filled.Image, Icons.AutoMirrored.Filled.Article).forEach { KindTile(it) }
        }
        Text(stringResource(R.string.create_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(stringResource(R.string.create_empty_message), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        MainButton(onAddFiles, enabled = true) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.create_add_files), modifier = Modifier.padding(start = Spacing))
        }
    }
}

@Composable
private fun KindTile(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(TileSize).clip(TileShape).background(colors.iconTile), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = colors.primary)
    }
}

@Composable
internal fun CreateList(
    state: CreateUiState,
    thumbnail: suspend (CreateItem) -> ImageBitmap?,
    onAddFiles: () -> Unit,
    onAction: (CreateAction) -> Unit,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // The gesture started on one row outlives many orders of the list.
    val latest by rememberUpdatedState(state.items)
    val colors = MaterialTheme.colorScheme
    LazyColumn(state = list, modifier = modifier, contentPadding = PaddingValues(horizontal = EdgePadding)) {
        item(key = HEADER) { ListHeader(state) }
        items(state.items, key = { it.id }) { item ->
            val dragged = item.id == draggedId
            CreateRow(
                item = item,
                thumbnail = thumbnail,
                onRemove = { onAction(CreateAction.Remove(item.id)) },
                onDragStart = {
                    draggedId = item.id
                    offset = 0f
                },
                onDrag = { amount ->
                    offset += amount.y
                    val height = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.id }?.size ?: return@CreateRow
                    val steps = (offset / height).roundToInt()
                    val index = latest.indexOfFirst { it.id == item.id }
                    val target = (index + steps).coerceIn(0, latest.lastIndex)
                    if (index >= 0 && target != index) {
                        onAction(CreateAction.Move(index, target))
                        offset -= (target - index) * height
                    }
                },
                onDragEnd = {
                    draggedId = null
                    offset = 0f
                },
                modifier = Modifier
                    .then(if (dragged) Modifier else Modifier.animateItem())
                    .graphicsLayer {
                        if (dragged) {
                            translationY = offset
                            scaleX = DRAGGED_SCALE
                            scaleY = DRAGGED_SCALE
                            shadowElevation = DragShadow.toPx()
                        }
                    }
                    .then(if (dragged) Modifier.background(colors.surface) else Modifier),
            )
        }
        item(key = ADD) { AddFilesRow(onAddFiles) }
        if (state.hasText) item(key = LAYOUT) { LayoutRow(state, onAction) }
    }
}

@Composable
private fun ListHeader(state: CreateUiState) {
    val colors = MaterialTheme.colorScheme
    val files = pluralStringResource(R.plurals.pages_split_files, state.items.size, state.items.size)
    val detail = if (state.unusable > 0) {
        pluralStringResource(R.plurals.create_unusable, state.unusable, state.unusable)
    } else {
        pluralStringResource(R.plurals.create_about_pages, state.totalPages, state.totalPages)
    }
    Text("$files · $detail", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing / 2))
}

@Composable
private fun AddFilesRow(onAddFiles: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing)
            .heightIn(min = DashedHeight)
            .clip(CardShape)
            .border(BorderStroke(DashedStroke, colors.outline), CardShape)
            .clickable(onClick = onAddFiles),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing / 2)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = colors.primary)
            Text(stringResource(R.string.create_add_files), color = colors.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LayoutRow(state: CreateUiState, onAction: (CreateAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CardShape, color = colors.surface, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing).clickable { onAction(CreateAction.OpenLayout) }) {
        Row(Modifier.padding(EdgePadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing)) {
            Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = colors.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.create_layout), style = MaterialTheme.typography.titleSmall)
                Text(layoutSummary(state.layout), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CreateFooter(state: CreateUiState, onCreate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(EdgePadding), verticalArrangement = Arrangement.spacedBy(Spacing)) {
            if (state.unusable > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = colors.error)
                    Text(pluralStringResource(R.plurals.create_remove_them, state.unusable, state.unusable), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    text = stringResource(R.string.create_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MainButton(onCreate, enabled = state.canCreate) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                Text(stringResource(R.string.create_title), modifier = Modifier.padding(start = Spacing))
            }
        }
    }
}

@Composable
private fun MainButton(onClick: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = BigButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth().heightIn(min = BigButtonHeight),
    ) {
        content()
    }
}

@Composable
internal fun LeaveListDialog(onAction: (CreateAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(CreateAction.KeepList) },
        title = { Text(stringResource(R.string.create_leave_title)) },
        text = { Text(stringResource(R.string.create_leave_message)) },
        confirmButton = { TextButton(onClick = { onAction(CreateAction.LeaveList) }) { Text(stringResource(R.string.create_leave)) } },
        dismissButton = { TextButton(onClick = { onAction(CreateAction.KeepList) }) { Text(stringResource(R.string.create_keep)) } },
    )
}
