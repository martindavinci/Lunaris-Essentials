package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.browse.EntryKinds
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.ui.BottomPanel
import io.github.martindavinci.lunaris.archives.ui.EntryTile
import io.github.martindavinci.lunaris.archives.ui.PanelActionRow
import io.github.martindavinci.lunaris.archives.ui.PanelHeader

private val BodyPadding = 24.dp

/** No app opens this kind of file: it can still be shared or extracted. */
@Composable
internal fun NoAppSheet(row: FolderRow, onAction: (BrowserAction) -> Unit) {
    val extension = row.name.substringAfterLast('.', "")
    BottomPanel(onDismiss = { onAction(BrowserAction.DismissSheet) }) {
        val title = if (extension.isEmpty()) stringResource(R.string.browser_no_app_plain) else stringResource(R.string.browser_no_app, extension)
        PanelHeader(title, row.name) { EntryTile(EntryKinds.of(row.name, EntryType.FILE)) }
        PanelActionRow(Icons.Rounded.Share, stringResource(R.string.action_share)) { onAction(BrowserAction.Share(listOf(row))) }
        PanelActionRow(Icons.Rounded.Unarchive, stringResource(R.string.entry_extract_to)) { onAction(BrowserAction.ExtractRows(listOf(row))) }
    }
}

/** A protected entry of a RAR 4 archive: nothing free can read it. */
@Composable
internal fun Rar4Sheet(onAction: (BrowserAction) -> Unit) {
    BottomPanel(onDismiss = { onAction(BrowserAction.DismissSheet) }) {
        PanelHeader(stringResource(R.string.problem_rar4_title), null)
        Text(
            stringResource(R.string.problem_rar4_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(BodyPadding),
        )
    }
}
