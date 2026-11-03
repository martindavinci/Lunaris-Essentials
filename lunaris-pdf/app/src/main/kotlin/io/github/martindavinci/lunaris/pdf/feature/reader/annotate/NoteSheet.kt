package io.github.martindavinci.lunaris.pdf.feature.reader.annotate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.feature.reader.EditAction
import io.github.martindavinci.lunaris.pdf.feature.reader.NoteDraft
import io.github.martindavinci.lunaris.pdf.feature.reader.ReaderAction
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel

private const val NOTE_MIN_LINES = 3
private const val NOTE_MAX_LINES = 8
private val SheetPadding = 20.dp
private val SheetSpacing = 12.dp

@Composable
internal fun NoteSheet(note: NoteDraft, onAction: (ReaderAction) -> Unit) {
    var text by rememberSaveable(note.id) { mutableStateOf(note.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(note.id) { focus.requestFocus() }
    BottomPanel(onDismiss = { onAction(EditAction.DismissNote) }, modifier = Modifier.imePadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = SheetPadding, end = SheetPadding, bottom = SheetPadding),
            verticalArrangement = Arrangement.spacedBy(SheetSpacing),
        ) {
            Text(stringResource(R.string.annotate_note_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.annotate_note_placeholder)) },
                minLines = NOTE_MIN_LINES,
                maxLines = NOTE_MAX_LINES,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(SheetSpacing, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onAction(EditAction.SaveNote(note.id, "")) }) {
                    Text(stringResource(R.string.annotate_delete))
                }
                Button(onClick = { onAction(EditAction.SaveNote(note.id, text)) }) {
                    Text(stringResource(R.string.annotate_save))
                }
            }
        }
    }
}
