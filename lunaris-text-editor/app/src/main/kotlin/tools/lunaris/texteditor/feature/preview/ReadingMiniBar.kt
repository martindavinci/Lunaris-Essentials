package tools.lunaris.texteditor.feature.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.tts.ReaderState

/** Collapsed bottom controller: play/pause, prev/next sentence, status, and expand chevron. */
@Composable
fun ReadingMiniBar(
    state: ReaderState,
    statusVoice: String,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth().padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (state.paused) R.string.tts_play else R.string.tts_pause),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.reading_in_progress), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = statusVoice + " · " + stringResource(
                        R.string.tts_sentence_progress, state.index + 1, state.total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.SkipPrevious, stringResource(R.string.tts_prev_sentence))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, stringResource(R.string.tts_next_sentence))
            }
            IconButton(onClick = onExpand) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.tts_expand))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ReadingMiniBarPreview() {
    tools.lunaris.texteditor.ui.theme.LunarisEditorTheme {
        ReadingMiniBar(
            state = ReaderState(reading = true, paused = false, index = 2, total = 6),
            statusVoice = "Italiano · 1.0×",
            onPlayPause = {}, onPrev = {}, onNext = {}, onExpand = {},
        )
    }
}
