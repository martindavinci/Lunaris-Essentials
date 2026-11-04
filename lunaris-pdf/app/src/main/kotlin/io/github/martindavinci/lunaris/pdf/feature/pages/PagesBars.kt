package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R

private val TopBarHeight = 64.dp
private val BarPadding = 12.dp
private val BarSpacing = 8.dp
private val BigButtonHeight = 52.dp
private val BigButtonShape = RoundedCornerShape(16.dp)
private val SavePadding = 16.dp
private val SaveShape = RoundedCornerShape(20.dp)
private val SaveHeight = 40.dp
private val ActionPillWidth = 56.dp
private val ActionPillHeight = 32.dp
private val ActionPillShape = RoundedCornerShape(16.dp)
private val BarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val ToolPadding = 4.dp

/** Undo, Redo and Save stay here whatever else happens below. */
@Composable
internal fun PagesTopBar(state: PagesUiState, subtitle: String, onBack: () -> Unit, onSave: () -> Unit, onAction: (PagesAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = TopBarHeight).padding(end = BarSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pages_title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.canEdit) {
                IconButton(onClick = { onAction(PagesAction.Undo) }, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.annotate_undo))
                }
                IconButton(onClick = { onAction(PagesAction.Redo) }, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.annotate_redo))
                }
                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    shape = SaveShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                    contentPadding = PaddingValues(horizontal = SavePadding),
                    modifier = Modifier.heightIn(min = SaveHeight),
                ) {
                    Text(stringResource(R.string.pages_save), maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun PagesDefaultBar(onAction: (PagesAction) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            // Equal heights, also when a long label in some language takes two lines.
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(BarPadding).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(BarSpacing),
        ) {
            BigBarButton(Icons.Filled.Add, stringResource(R.string.pages_add), Modifier.weight(1f)) { onAction(PagesAction.OpenAdd) }
            BigBarButton(Icons.Filled.CallSplit, stringResource(R.string.pages_split), Modifier.weight(1f)) { onAction(PagesAction.OpenSplit) }
        }
    }
}

@Composable
private fun BigBarButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        shape = BigButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant, contentColor = colors.onSurface),
        modifier = modifier.fillMaxHeight().heightIn(min = BigButtonHeight),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = BarSpacing), textAlign = TextAlign.Center)
    }
}

/** Takes the place of the bar below, never covering the pages. */
@Composable
internal fun PagesSelectionBar(count: Int, onExtract: () -> Unit, onAction: (PagesAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, shape = BarShape) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(BarPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.pages_selected, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = BarSpacing),
                )
                TextButton(onClick = { onAction(PagesAction.SelectAll) }) { Text(stringResource(R.string.pages_select_all)) }
                IconButton(onClick = { onAction(PagesAction.ClearSelection) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pages_clear_selection))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                SelectionTool(Icons.Filled.RotateRight, stringResource(R.string.pages_rotate), Modifier.weight(1f)) { onAction(PagesAction.RotateSelected) }
                SelectionTool(Icons.Filled.Delete, stringResource(R.string.pages_delete), Modifier.weight(1f)) { onAction(PagesAction.DeleteSelected) }
                SelectionTool(Icons.Filled.FileUpload, stringResource(R.string.pages_extract), Modifier.weight(1f), onExtract)
            }
        }
    }
}

@Composable
private fun SelectionTool(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = ToolPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = ActionPillShape, color = colors.surfaceVariant) {
            Box(Modifier.size(ActionPillWidth, ActionPillHeight), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = ToolPadding), textAlign = TextAlign.Center)
    }
}

/** For a protected file: the only thing left to do. */
@Composable
internal fun PagesBackBar(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        Button(
            onClick = onBack,
            shape = BigButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(BarPadding).heightIn(min = BigButtonHeight),
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
            Text(stringResource(R.string.pages_back_to_reading), modifier = Modifier.padding(start = BarSpacing))
        }
    }
}

/** What dragging says at the top of the grid. */
@Composable
internal fun MovePill(count: Int, place: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = SaveShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = modifier) {
        Text(
            text = if (place == 0) {
                pluralStringResource(R.plurals.pages_move_start, count, count)
            } else {
                pluralStringResource(R.plurals.pages_move_after, count, count, place)
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = SavePadding, vertical = BarSpacing),
        )
    }
}
