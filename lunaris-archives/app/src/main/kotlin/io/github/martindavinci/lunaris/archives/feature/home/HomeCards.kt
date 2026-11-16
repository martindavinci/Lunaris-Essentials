package io.github.martindavinci.lunaris.archives.feature.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.archive.EntryNames
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import io.github.martindavinci.lunaris.archives.ui.FormatBadge
import io.github.martindavinci.lunaris.archives.ui.PATH_SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.sizeText
import io.github.martindavinci.lunaris.archives.ui.theme.accentText
import io.github.martindavinci.lunaris.archives.ui.theme.iconTile

private val CardMargin = 16.dp
private val CardShape = RoundedCornerShape(24.dp)
private val CardPadding = 16.dp
private val CardSpacing = 16.dp
private val ContinueTop = 4.dp
private val ContinueTileSize = 56.dp
private val ContinueTileShape = RoundedCornerShape(16.dp)
private val ContinueIcon = 28.dp
private val NameSpacing = 8.dp
private val CreateTop = 12.dp
private val CreateMinHeight = 72.dp
private val CreateEnd = 8.dp
private val CreateTileSize = 40.dp
private val CreateTileShape = RoundedCornerShape(12.dp)
private val BorderWidth = 1.dp
private val ChevronBoxWidth = 40.dp
private val ChevronBoxHeight = 48.dp
private val SectionTop = 20.dp
private val SectionBottom = 4.dp

/** The last archive, one tap away, at the folder the user was in. */
@Composable
internal fun ContinueCard(item: RecentArchive, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CardShape, color = colors.surface, modifier = Modifier.fillMaxWidth().padding(start = CardMargin, end = CardMargin, top = ContinueTop)) {
        Row(Modifier.padding(CardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
            ContinueTile(colors.accentText)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_continue), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NameSpacing)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false))
                    FormatBadge(item.formatLabel)
                }
                Text(continueMeta(item), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Folder (when not the root), archive size, when it was opened: no item count, it is not known here. */
@Composable
private fun continueMeta(item: RecentArchive): String {
    val folder = item.lastPath?.takeIf { it.isNotEmpty() }?.joinToString(PATH_SEPARATOR) { EntryNames.display(it) }
    val size = item.sizeBytes?.let { sizeText(it) }
    return listOfNotNull(folder, size, openedText(item.lastOpenedMillis).takeIf { it.isNotEmpty() }).joinToString(SEPARATOR)
}

/** Frame 02: the place of the Continue card while there is nothing to continue. */
@Composable
internal fun ContinuePlaceholder(rememberRecents: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CardShape, color = colors.surface, modifier = Modifier.fillMaxWidth().padding(start = CardMargin, end = CardMargin, top = ContinueTop)) {
        Row(Modifier.padding(CardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
            ContinueTile(colors.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(if (rememberRecents) R.string.home_empty_detail else R.string.home_recents_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContinueTile(tint: Color) {
    Box(Modifier.size(ContinueTileSize).background(MaterialTheme.colorScheme.surfaceVariant, ContinueTileShape), Alignment.Center) {
        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = tint, modifier = Modifier.size(ContinueIcon))
    }
}

@Composable
internal fun CreateCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = colors.background,
        border = BorderStroke(BorderWidth, colors.outline),
        modifier = Modifier.fillMaxWidth().padding(start = CardMargin, end = CardMargin, top = CreateTop),
    ) {
        Row(
            Modifier.heightIn(min = CreateMinHeight).padding(start = CardPadding, end = CreateEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            Box(Modifier.size(CreateTileSize).background(colors.iconTile, CreateTileShape), Alignment.Center) {
                Icon(Icons.Rounded.FolderZip, contentDescription = null, tint = colors.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_create), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.home_create_detail), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Box(Modifier.size(ChevronBoxWidth, ChevronBoxHeight), Alignment.Center) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = CardMargin, end = CardMargin, top = SectionTop, bottom = SectionBottom),
    )
}
