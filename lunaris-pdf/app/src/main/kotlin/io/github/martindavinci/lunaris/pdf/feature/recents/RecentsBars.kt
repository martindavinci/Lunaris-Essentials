package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel

private val BarHeight = 64.dp
private val TitlePadding = 16.dp
private val FieldHeight = 48.dp
private val FieldShape = RoundedCornerShape(24.dp)
private val FieldPadding = 16.dp
private val FieldEndMargin = 12.dp
private val PanelPadding = 24.dp
private val PanelBottomPadding = 12.dp
private val PanelRowPadding = 16.dp
private val PanelRowSpacing = 16.dp
private val PanelRowHeight = 56.dp

@Composable
internal fun RecentsTopBar(
    query: String?,
    hasEntries: Boolean,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onAction: (RecentsAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = BarHeight), verticalAlignment = Alignment.CenterVertically) {
        if (query != null) {
            IconButton(onClick = { onAction(RecentsAction.CloseSearch) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            SearchField(query, onChange = { onAction(RecentsAction.SetQuery(it)) }, modifier = Modifier.weight(1f))
        } else {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = TitlePadding),
            )
            if (hasEntries) {
                IconButton(onClick = { onAction(RecentsAction.OpenSearch) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.recents_search))
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.recents_clear_title))
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(shape = FieldShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.padding(end = FieldEndMargin)) {
        Row(Modifier.heightIn(min = FieldHeight).padding(horizontal = FieldPadding), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

/** What can be done with one document of the list. */
@Composable
internal fun RecentPanel(entry: RecentEntry, onAction: (RecentsAction) -> Unit, onDismiss: () -> Unit) {
    BottomPanel(onDismiss = onDismiss) {
        Column(Modifier.padding(start = PanelPadding, end = PanelPadding, bottom = PanelBottomPadding)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            RecentMeta(entry)
        }
        PanelRow(Icons.Filled.PushPin, if (entry.pinned) R.string.recents_unpin else R.string.recents_pin) {
            onDismiss()
            onAction(RecentsAction.SetPinned(entry, !entry.pinned))
        }
        PanelRow(Icons.Filled.Share, R.string.action_share) {
            onDismiss()
            onAction(RecentsAction.ShareEntry(entry))
        }
        PanelRow(Icons.Filled.DeleteOutline, R.string.recents_remove) {
            onDismiss()
            onAction(RecentsAction.RemoveEntry(entry))
        }
    }
}

@Composable
private fun PanelRow(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = PanelRowHeight)
            .padding(horizontal = PanelPadding, vertical = PanelRowPadding / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PanelRowSpacing),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}
