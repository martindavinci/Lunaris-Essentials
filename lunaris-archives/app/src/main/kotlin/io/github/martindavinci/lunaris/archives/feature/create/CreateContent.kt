package io.github.martindavinci.lunaris.archives.feature.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.browse.EntryKind
import io.github.martindavinci.lunaris.archives.browse.EntryKinds
import io.github.martindavinci.lunaris.archives.create.CreateItem
import io.github.martindavinci.lunaris.archives.ui.EntryTile
import io.github.martindavinci.lunaris.archives.ui.SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.countText
import io.github.martindavinci.lunaris.archives.ui.sizeText
import io.github.martindavinci.lunaris.archives.ui.theme.accentText

private val CardsPadding = 16.dp
private val CardsTop = 8.dp
private val CardsSpacing = 12.dp
private val CardMinHeight = 120.dp
private val CardShape = RoundedCornerShape(24.dp)
private val CardInnerPadding = 16.dp
private val CardIconSize = 28.dp
private val BadgeSize = 24.dp
private val BadgeIconSize = 16.dp
private val BorderWidth = 1.dp
private val SummaryMinHeight = 52.dp
private val SummaryStart = 16.dp
private val SummaryEnd = 8.dp
private val SummaryTop = 12.dp
private val RowMinHeight = 64.dp
private val RowStart = 16.dp
private val RowEnd = 4.dp
private val RowSpacing = 16.dp
private val RemoveIconSize = 20.dp
private val AddIconSize = 20.dp
private val AddSpacing = 6.dp
private val DividerTop = 8.dp

/** Frame 13: where the content comes from, and what has been picked so far. */
@Composable
internal fun CreateContent(state: CreateUiState, onAction: (CreateAction) -> Unit, onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = CardsPadding, end = CardsPadding, top = CardsTop), horizontalArrangement = Arrangement.spacedBy(CardsSpacing)) {
        SourceCard(Icons.Rounded.FileCopy, stringResource(R.string.create_pick_files), state.pickedFiles, onPickFiles, Modifier.weight(1f))
        SourceCard(Icons.Rounded.Folder, stringResource(R.string.create_pick_folder), state.pickedFolder, onPickFolder, Modifier.weight(1f))
    }
    if (state.items.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().heightIn(min = SummaryMinHeight).padding(start = SummaryStart, end = SummaryEnd, top = SummaryTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val files = state.items.sumOf { it.fileCount }
        Text(
            text = countText(R.plurals.count_files, files) + SEPARATOR + sizeText(state.totalBytes),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            modifier = Modifier.weight(1f),
        )
        // Opens again the picker that was used last.
        TextButton(onClick = if (state.pickedFolder && !state.pickedFiles) onPickFolder else onPickFiles) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(AddIconSize))
            Text(stringResource(R.string.create_add_more), color = colors.accentText, modifier = Modifier.padding(start = AddSpacing))
        }
    }
    state.items.forEach { item -> PickedRow(item, onRemove = { onAction(CreateAction.Remove(item)) }) }
    HorizontalDivider(Modifier.padding(top = DividerTop), color = colors.surfaceVariant)
}

/** "Pick files" / "Pick a folder". Filled, with a check, once it has given content. */
@Composable
private fun SourceCard(icon: ImageVector, label: String, picked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (picked) colors.primaryContainer else colors.background,
        contentColor = if (picked) colors.onPrimaryContainer else colors.onSurface,
        border = if (picked) null else BorderStroke(BorderWidth, colors.outline),
        modifier = modifier.heightIn(min = CardMinHeight),
    ) {
        Box(Modifier.padding(CardInnerPadding)) {
            Column(Modifier.heightIn(min = CardMinHeight - CardInnerPadding - CardInnerPadding), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, contentDescription = null, tint = if (picked) colors.onPrimaryContainer else colors.accentText, modifier = Modifier.size(CardIconSize))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            if (picked) {
                Box(Modifier.align(Alignment.TopEnd).size(BadgeSize).background(colors.accentText, CircleShape), Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.create_picked), tint = colors.onPrimary, modifier = Modifier.size(BadgeIconSize))
                }
            }
        }
    }
}

@Composable
private fun PickedRow(item: CreateItem, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val kind = if (item.isFolder) EntryKind.FOLDER else EntryKinds.of(item.name, EntryType.FILE)
    val size = item.sizeBytes?.let { sizeText(it) }
    val meta = when {
        item.isFolder && size != null -> countText(R.plurals.count_files, item.fileCount) + SEPARATOR + size
        item.isFolder -> countText(R.plurals.count_files, item.fileCount)
        else -> size ?: stringResource(R.string.size_unknown)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(start = RowStart, end = RowEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        EntryTile(kind)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.create_remove, item.name), tint = colors.onSurfaceVariant, modifier = Modifier.size(RemoveIconSize))
        }
    }
}
