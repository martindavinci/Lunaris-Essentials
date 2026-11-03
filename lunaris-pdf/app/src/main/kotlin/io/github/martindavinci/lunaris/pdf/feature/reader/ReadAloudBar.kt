package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState

private val BarHeight = 56.dp
private val BarElevation = 3.dp
private val TextPadding = 16.dp

/** Stays up while reading aloud, with or without the other bars. */
@Composable
internal fun ReadAloudBar(state: ReadAloudState, onAction: (ReaderAction) -> Unit) {
    Surface(tonalElevation = BarElevation) {
        Row(Modifier.fillMaxWidth().heightIn(min = BarHeight), verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                is ReadAloudState.Reading -> Controls(state.page, playing = true, onAction)
                is ReadAloudState.Paused -> Controls(state.page, playing = false, onAction)
                is ReadAloudState.NoText -> Message(R.string.read_aloud_no_text)
                ReadAloudState.Unavailable -> Message(R.string.read_aloud_unavailable)
                ReadAloudState.Idle -> Unit
            }
            IconButton(onClick = { onAction(ReaderAction.StopReadAloud) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.read_aloud_stop))
            }
        }
    }
}

@Composable
private fun RowScope.Controls(
    page: Int,
    playing: Boolean,
    onAction: (ReaderAction) -> Unit,
) {
    Text(
        text = stringResource(R.string.read_aloud_page, page),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = TextPadding).weight(1f),
    )
    IconButton(onClick = { onAction(ReaderAction.PreviousReadAloudPage) }) {
        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.read_aloud_previous))
    }
    if (playing) {
        IconButton(onClick = { onAction(ReaderAction.PauseReadAloud) }) {
            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.read_aloud_pause))
        }
    } else {
        IconButton(onClick = { onAction(ReaderAction.ResumeReadAloud) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.read_aloud_resume))
        }
    }
    IconButton(onClick = { onAction(ReaderAction.NextReadAloudPage) }) {
        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.read_aloud_next))
    }
}

@Composable
private fun RowScope.Message(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = TextPadding).weight(1f),
    )
}
