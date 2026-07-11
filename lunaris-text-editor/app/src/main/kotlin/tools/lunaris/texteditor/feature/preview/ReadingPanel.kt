package tools.lunaris.texteditor.feature.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.tts.ReaderState
import tools.lunaris.texteditor.tts.VoiceGroup

private val SPEED_RANGE = 0.5f..4f

/** Expanded controller: progress, transport, voice list, and speed slider. */
@Composable
fun ReadingPanel(
    state: ReaderState,
    voiceGroups: List<VoiceGroup>,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCollapse: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onRate: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 4.dp, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.preview_read_aloud),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.tts_collapse))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    stringResource(R.string.tts_sentence_progress, state.index + 1, state.total),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(rateLabel(state.rate), style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else (state.index + 1f) / state.total },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, stringResource(R.string.tts_prev_sentence)) }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp)) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                        Icon(
                            imageVector = if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = stringResource(if (state.paused) R.string.tts_play else R.string.tts_pause),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, stringResource(R.string.tts_next_sentence)) }
            }
            Text(stringResource(R.string.tts_voice), style = MaterialTheme.typography.labelMedium)
            // Bounded height: a LazyColumn inside a non-scrolling Column must not get an infinite
            // height constraint, or measurement throws.
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(vertical = 4.dp)) {
                voiceGroups.forEach { group ->
                    items(group.voices, key = { it.id }) { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelectVoice(option.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (option.id == state.voiceId) Icon(Icons.Filled.Check, stringResource(R.string.tts_voice_selected))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tts_speed), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(rateLabel(state.rate), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
            Slider(
                value = state.rate.coerceIn(SPEED_RANGE),
                onValueChange = onRate,
                valueRange = SPEED_RANGE,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun rateLabel(rate: Float): String = String.format(java.util.Locale.US, "%.1f×", rate)

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ReadingPanelPreview() {
    tools.lunaris.texteditor.ui.theme.LunarisEditorTheme {
        ReadingPanel(
            state = ReaderState(reading = true, index = 2, total = 6, rate = 1f, voiceId = "it-it-x-itc-local"),
            voiceGroups = listOf(
                VoiceGroup("Italiano", listOf(
                    tools.lunaris.texteditor.tts.VoiceOption("it-it-x-itc-local", "Italiano (Italia) · itc · offline", false),
                )),
            ),
            onPlayPause = {}, onPrev = {}, onNext = {}, onCollapse = {}, onSelectVoice = {}, onRate = {},
        )
    }
}
