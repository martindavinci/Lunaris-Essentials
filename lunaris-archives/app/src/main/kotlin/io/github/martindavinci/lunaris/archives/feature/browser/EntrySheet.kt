package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.browse.EntryKinds
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.ui.BottomPanel
import io.github.martindavinci.lunaris.archives.ui.DetailRow
import io.github.martindavinci.lunaris.archives.ui.DetailText
import io.github.martindavinci.lunaris.archives.ui.DetailsBox
import io.github.martindavinci.lunaris.archives.ui.EntryTile
import io.github.martindavinci.lunaris.archives.ui.PanelActionRow
import io.github.martindavinci.lunaris.archives.ui.PanelHeader
import io.github.martindavinci.lunaris.archives.ui.countText
import io.github.martindavinci.lunaris.archives.ui.entryDateTime
import io.github.martindavinci.lunaris.archives.ui.sizeText

/** Frame 05: what can be done with one file, or a folder, and what is known about it. */
@Composable
internal fun EntrySheet(row: FolderRow, onAction: (BrowserAction) -> Unit) {
    val kind = EntryKinds.of(row.name, if (row.isFolder) EntryType.DIRECTORY else EntryType.FILE)
    BottomPanel(onDismiss = { onAction(BrowserAction.DismissSheet) }) {
        PanelHeader(row.name, row.path.joinToString(PATH_SEPARATOR)) { EntryTile(kind) }
        if (row.isFolder) {
            PanelActionRow(Icons.Rounded.Unarchive, stringResource(R.string.entry_extract_folder)) {
                onAction(BrowserAction.ExtractRows(listOf(row)))
            }
            DetailsBox {
                DetailRow(stringResource(R.string.entry_contains, countText(R.plurals.count_items, row.childCount))) {}
                DetailRow(stringResource(R.string.entry_size)) { DetailText(sizeText(row.totalSize)) }
            }
        } else {
            PanelActionRow(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.entry_open_with)) { onAction(BrowserAction.OpenWith(row)) }
            PanelActionRow(Icons.Rounded.Share, stringResource(R.string.action_share)) { onAction(BrowserAction.Share(listOf(row))) }
            PanelActionRow(Icons.Rounded.Unarchive, stringResource(R.string.entry_extract_to)) { onAction(BrowserAction.ExtractRows(listOf(row))) }
            FileDetails(row)
        }
    }
}

@Composable
private fun FileDetails(row: FolderRow) {
    val entry = row.entry ?: return
    DetailsBox {
        DetailRow(stringResource(R.string.entry_size)) { DetailText(entry.size?.let { sizeText(it) } ?: stringResource(R.string.size_unknown)) }
        entry.packedSize?.let { packed -> DetailRow(stringResource(R.string.entry_packed)) { DetailText(sizeText(packed)) } }
        entry.modifiedMillis?.let { millis -> DetailRow(stringResource(R.string.entry_modified)) { DetailText(entryDateTime(millis)) } }
        DetailRow(stringResource(R.string.entry_encrypted)) { DetailText(stringResource(if (entry.encrypted) R.string.yes else R.string.no)) }
    }
}

private const val PATH_SEPARATOR = "/"
