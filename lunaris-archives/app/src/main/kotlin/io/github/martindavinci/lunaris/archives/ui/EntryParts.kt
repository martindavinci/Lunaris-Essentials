package io.github.martindavinci.lunaris.archives.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.browse.EntryKind
import io.github.martindavinci.lunaris.archives.ui.theme.accentText
import io.github.martindavinci.lunaris.archives.ui.theme.entryTile

private val BadgeHeight = 20.dp
private val BadgePadding = 7.dp
private val BadgeShape = RoundedCornerShape(10.dp)
private val FormatTileSize = 48.dp
private val FormatTileShape = RoundedCornerShape(14.dp)
private val EntryTileSize = 40.dp
private val EntryTileShape = RoundedCornerShape(12.dp)
private val LockSize = 15.dp
private val LockSpacing = 4.dp

/** "ZIP", "TAR.GZ": the small pill after an archive's name. */
@Composable
fun FormatBadge(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.heightIn(min = BadgeHeight).background(colors.surfaceVariant, BadgeShape).padding(horizontal = BadgePadding), Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
    }
}

/** The square with the format in front of a recent archive. */
@Composable
fun FormatTile(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(FormatTileSize).background(colors.surfaceVariant, FormatTileShape), Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

fun EntryKind.icon(): ImageVector = when (this) {
    EntryKind.FOLDER -> Icons.Rounded.Folder
    EntryKind.ARCHIVE -> Icons.Rounded.FolderZip
    EntryKind.TEXT -> Icons.Rounded.Description
    EntryKind.TABLE -> Icons.Rounded.TableChart
    EntryKind.PDF -> Icons.Rounded.PictureAsPdf
    EntryKind.IMAGE -> Icons.Rounded.Image
    EntryKind.VIDEO -> Icons.Rounded.Movie
    EntryKind.AUDIO -> Icons.Rounded.MusicNote
    EntryKind.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

@Composable
fun EntryKind.tint(): Color =
    if (this == EntryKind.FOLDER) MaterialTheme.colorScheme.accentText else MaterialTheme.colorScheme.onSurfaceVariant

/** The icon in front of an entry; a filled circle with a check when the row is selected. */
@Composable
fun EntryTile(kind: EntryKind, selected: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    if (selected) {
        Box(Modifier.size(EntryTileSize).background(colors.primary, CircleShape), Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary)
        }
    } else {
        Box(Modifier.size(EntryTileSize).background(colors.entryTile, EntryTileShape), Alignment.Center) {
            Icon(kind.icon(), contentDescription = null, tint = kind.tint())
        }
    }
}

/** The second line of a row, with a lock in front when the entry needs a password. */
@Composable
fun MetaLine(text: String, locked: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LockSpacing)) {
        if (locked) {
            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.home_locked), tint = color, modifier = Modifier.size(LockSize))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
