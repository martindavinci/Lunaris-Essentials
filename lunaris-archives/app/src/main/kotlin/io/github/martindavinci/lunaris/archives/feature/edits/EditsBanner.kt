package io.github.martindavinci.lunaris.archives.feature.edits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.edits.ChangedArchive
import io.github.martindavinci.lunaris.archives.edits.EditRecord
import io.github.martindavinci.lunaris.archives.edits.Snapshot
import io.github.martindavinci.lunaris.archives.settings.ThemeMode
import io.github.martindavinci.lunaris.archives.ui.theme.LunarisArchivesTheme
import io.github.martindavinci.lunaris.archives.ui.theme.accentText
import io.github.martindavinci.lunaris.archives.ui.theme.onWarningContainer
import io.github.martindavinci.lunaris.archives.ui.theme.warningContainer

private val CardShape = RoundedCornerShape(20.dp)
private val CardPadding = 16.dp
private val Gap = 8.dp
private val IconSize = 24.dp
private val OuterPadding = 16.dp

/** One card per archive with changed files: what changed, and Update in ZIP / Save a copy / Ignore. */
@Composable
internal fun EditsCards(state: EditsUiState, archiveUri: String?, onAction: (EditsAction) -> Unit, onSaveCopy: (ChangedArchive) -> Unit) {
    val shown = state.archives.filter { archiveUri == null || it.archiveUri == archiveUri }
    shown.forEach { archive ->
        EditsCard(archive, working = state.working == archive.archiveUri, saving = state.saving, busy = state.working != null,
            expanded = archive.archiveUri in state.expanded, onAction = onAction, onSaveCopy = { onSaveCopy(archive) })
    }
}

@Composable
private fun EditsCard(
    archive: ChangedArchive, working: Boolean, saving: Boolean, busy: Boolean, expanded: Boolean,
    onAction: (EditsAction) -> Unit, onSaveCopy: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val count = archive.entries.size
    val title = if (count == 1) stringResource(R.string.edits_changed_one, archive.entries.single().entryPath.substringAfterLast('/'))
        else pluralStringResource(R.plurals.edits_changed_many, count, count)
    Surface(shape = CardShape, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(horizontal = OuterPadding, vertical = Gap)) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(IconSize))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val where = if (archive.updatable) stringResource(R.string.edits_in_archive, archive.archiveName)
                        else stringResource(R.string.edits_cannot_update, archive.archiveName)
                    Text(where, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (expanded) archive.entries.forEach { Text(it.entryPath, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            if (working) {
                Text(
                    if (saving) stringResource(R.string.edits_saving) else stringResource(R.string.edits_working, archive.archiveName),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    if (archive.updatable) {
                        FilledTonalButton(onClick = { onAction(EditsAction.Update(archive.archiveUri)) }, enabled = !busy) {
                            Text(stringResource(R.string.edits_update))
                        }
                    }
                    TextButton(onClick = onSaveCopy, enabled = !busy) { Text(stringResource(R.string.edits_save_copy)) }
                    TextButton(onClick = { onAction(EditsAction.Ignore(archive.archiveUri)) }, enabled = !busy) { Text(stringResource(R.string.edits_ignore)) }
                    if (count > 1) {
                        TextButton(onClick = { onAction(EditsAction.ToggleShow(archive.archiveUri)) }) {
                            Text(stringResource(if (expanded) R.string.edits_hide else R.string.edits_show))
                        }
                    }
                }
            }
        }
    }
}

/** Home only: the copy of an archive that could not be put back, until the user saves it or lets it go. */
@Composable
internal fun StuckCards(state: EditsUiState, onAction: (EditsAction) -> Unit, onSave: (id: String, name: String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    state.stuck.forEach { stuck ->
        Surface(shape = CardShape, color = colors.warningContainer, contentColor = colors.onWarningContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = OuterPadding, vertical = Gap)) {
            Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(Gap)) {
                Text(stringResource(R.string.edits_stuck_title, stuck.name), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.edits_stuck_body), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    FilledTonalButton(onClick = { onSave(stuck.id, stuck.name) }, enabled = state.working == null) {
                        Text(stringResource(R.string.edits_save_original))
                    }
                    TextButton(onClick = { onAction(EditsAction.DiscardBackup(stuck.id)) }) { Text(stringResource(R.string.edits_discard_copy)) }
                }
            }
        }
    }
}

private fun previewRecord(path: String, updatable: Boolean = true) = EditRecord(
    path, "/c/$path", "content://a", "k", "Lisbon 2026.zip", 1, 1, "f", "ZIP", path, 1, 1, "s", updatable, 1, Snapshot("/p", 1, 1, "t"),
)

@Preview
@Composable
private fun EditsCardPreview() = LunarisArchivesTheme(ThemeMode.DARK) {
    Column {
        EditsCards(EditsUiState(archives = listOf(ChangedArchive("content://a", "Lisbon 2026.zip", true, listOf(previewRecord("Lisbon 2026/Notes/Itinerary.md"))))), null, {}, {})
        EditsCards(EditsUiState(archives = listOf(ChangedArchive("content://b", "Project.7z", false, listOf(previewRecord("a.md", false), previewRecord("b.md", false))))), null, {}, {})
    }
}
