package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.browse.EntryKind
import io.github.martindavinci.lunaris.archives.ui.theme.selectedRow

private const val LARGE_FONT_SCALE = 1.5f
private val RowMinHeight = 64.dp
private val RowStart = 16.dp
private val RowEndWithButton = 4.dp
private val RowEnd = 16.dp
private val RowSpacing = 16.dp
private val RowVertical = 8.dp

/**
 * One file or folder of the archive (frames 03, 05, 06, 07).
 * [selected] is null outside selection mode; in it the row says whether it is selected, with a
 * check and not only with colour. [onMore] is null where the three dots are not shown.
 */
@Composable
fun EntryRow(
    name: AnnotatedString,
    meta: String,
    kind: EntryKind,
    locked: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected == true) colors.selectedRow else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { if (selected != null) this.selected = selected }
            .heightIn(min = RowMinHeight)
            .padding(start = RowStart, end = if (onMore != null) RowEndWithButton else RowEnd, top = RowVertical, bottom = RowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        EntryTile(kind, selected = selected == true)
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                // The end of a name says what the file is: keep it, unless the text is so large that two lines read better.
                maxLines = if (largeText) 2 else 1,
                overflow = if (largeText) TextOverflow.Ellipsis else TextOverflow.MiddleEllipsis,
            )
            MetaLine(meta, locked)
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options), tint = colors.onSurfaceVariant)
            }
        }
    }
}
