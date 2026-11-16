package io.github.martindavinci.lunaris.archives.feature.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.archive.EntryNames
import io.github.martindavinci.lunaris.archives.extract.Conflict
import io.github.martindavinci.lunaris.archives.extract.ConflictChoice
import io.github.martindavinci.lunaris.archives.extract.ExtractFailure
import io.github.martindavinci.lunaris.archives.ui.AccentTextButton
import io.github.martindavinci.lunaris.archives.ui.CheckRow
import io.github.martindavinci.lunaris.archives.ui.ChoiceRow
import io.github.martindavinci.lunaris.archives.ui.DeepButton
import io.github.martindavinci.lunaris.archives.ui.PATH_SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.countText

private val ChoiceSpacing = 4.dp
private val DividerPadding = 8.dp

/**
 * Frame 10: one conflict at a time, "Keep both" chosen, "do this for all" ticked.
 * "Replace" is not offered when what is in the way is a folder ([ExtractUiState.replaceable]).
 */
@Composable
internal fun ConflictDialog(conflict: Conflict, total: Int, state: ExtractUiState, onAction: (ExtractAction) -> Unit) {
    val name = EntryNames.display(conflict.file.name)
    val folder = state.location?.substringAfterLast(PATH_SEPARATOR)
    AlertDialog(
        onDismissRequest = { onAction(ExtractAction.CancelConflicts) },
        title = {
            val title = if (folder != null) stringResource(R.string.conflict_title, name, folder) else stringResource(R.string.conflict_title_plain, name)
            Text(title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ChoiceSpacing)) {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ChoiceSpacing)) {
                    if (state.replaceable) Choice(R.string.conflict_replace, null, ConflictChoice.REPLACE, state, onAction)
                    Choice(R.string.conflict_keep_both, state.keepBothName.takeIf { it.isNotEmpty() }, ConflictChoice.KEEP_BOTH, state, onAction)
                    Choice(R.string.conflict_skip, null, ConflictChoice.SKIP, state, onAction)
                }
                if (state.showApplyAll) {
                    HorizontalDivider(Modifier.padding(vertical = DividerPadding), color = MaterialTheme.colorScheme.outline)
                    CheckRow(countText(R.plurals.conflict_apply_all, total), state.applyAll, onChange = { onAction(ExtractAction.SetApplyAll(it)) })
                }
            }
        },
        dismissButton = { AccentTextButton(stringResource(R.string.cancel), onClick = { onAction(ExtractAction.CancelConflicts) }) },
        confirmButton = { DeepButton(stringResource(R.string.action_continue), onClick = { onAction(ExtractAction.ContinueConflicts) }) },
    )
}

@Composable
private fun Choice(label: Int, detail: String?, choice: ConflictChoice, state: ExtractUiState, onAction: (ExtractAction) -> Unit) {
    ChoiceRow(stringResource(label), detail, selected = state.choice == choice, onClick = { onAction(ExtractAction.PickChoice(choice)) })
}

/** "Stop extracting?" Asked for Cancel and for Back while files are being written. */
@Composable
internal fun StopDialog(onKeepGoing: () -> Unit, onStop: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text(stringResource(R.string.extract_stop_title)) },
        text = { Text(stringResource(R.string.extract_stop_body)) },
        dismissButton = { AccentTextButton(stringResource(R.string.extract_keep_going), onKeepGoing) },
        confirmButton = { AccentTextButton(stringResource(R.string.extract_stop), onStop) },
    )
}

/** An entry that grows far beyond its packed size. Leaving the dialog any other way than "Continue" stops that entry. */
@Composable
internal fun LargeFileDialog(onContinue: () -> Unit, onStop: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStop,
        title = { Text(stringResource(R.string.large_title)) },
        text = { Text(stringResource(R.string.large_body)) },
        dismissButton = { AccentTextButton(stringResource(R.string.extract_stop), onStop) },
        confirmButton = { AccentTextButton(stringResource(R.string.action_continue), onContinue) },
    )
}

/** Why the extraction could not go on. "Folder gone" offers to choose it again; the others only close. */
@Composable
internal fun FailureDialog(reason: ExtractFailure, onAction: (ExtractAction) -> Unit) {
    val (title, body) = when (reason) {
        ExtractFailure.NO_SPACE -> R.string.space_title to R.string.space_body_before
        ExtractFailure.RAN_OUT_OF_SPACE -> R.string.space_title to R.string.space_body_during
        ExtractFailure.FOLDER_GONE -> R.string.extract_folder_gone_title to R.string.extract_folder_gone
        ExtractFailure.READER_STOPPED -> R.string.problem_stopped_title to R.string.problem_stopped_body
        ExtractFailure.WRONG_PASSWORD -> R.string.extract_password_title to R.string.extract_password_body
    }
    AlertDialog(
        onDismissRequest = { onAction(ExtractAction.Done) },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        dismissButton = {
            if (reason == ExtractFailure.FOLDER_GONE) AccentTextButton(stringResource(R.string.extract_choose), onClick = { onAction(ExtractAction.ChooseAgain) })
        },
        confirmButton = { AccentTextButton(stringResource(R.string.ok), onClick = { onAction(ExtractAction.Done) }) },
    )
}
