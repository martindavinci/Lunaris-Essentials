package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState
import io.github.martindavinci.lunaris.pdf.tts.SpeechRates
import io.github.martindavinci.lunaris.pdf.tts.VoiceCatalog
import io.github.martindavinci.lunaris.pdf.tts.VoiceOption
import io.github.martindavinci.lunaris.pdf.ui.BottomPanel

private const val SEPARATOR = " · "
private const val PANEL_HEIGHT_FRACTION = 0.68f
private val EdgePadding = 24.dp
private val SectionTop = 16.dp
private val SectionBottom = 4.dp
private val ProgressPadding = 8.dp
private val TransportPadding = 8.dp
private val TransportSpacing = 8.dp
private val BigPlaySize = 64.dp
private val BigPlayIconPadding = 18.dp
private val RowHeight = 48.dp
private val GroupTop = 12.dp

/** All the controls of reading aloud: what is being read, how fast, and in which voice. */
@Composable
internal fun ReadAloudPanel(state: ReaderUiState, onAction: (ReaderAction) -> Unit, onCollapse: () -> Unit) {
    val preferred = LocalConfiguration.current.locales[0]
    val groups = remember(state.readAloudVoices, preferred) { VoiceCatalog.group(state.readAloudVoices, preferred) }
    val selected = state.readAloudVoice
    // Not the whole screen: the sentence being read stays in sight above it.
    BottomPanel(onDismiss = onCollapse, modifier = Modifier.fillMaxHeight(PANEL_HEIGHT_FRACTION)) {
        LazyColumn {
            item { Header(onCollapse) }
            item { Progress(state) }
            item { Transport(state, onAction) }
            item { Speed(state.readAloudRate, onAction) }
            if (groups.isNotEmpty()) {
                item { SectionLabel(R.string.read_aloud_voice) }
                item {
                    VoiceRow(stringResource(R.string.read_aloud_default_voice), selected == null) {
                        onAction(ReaderAction.SetReadAloudVoice(null))
                    }
                }
            }
            groups.forEach { group ->
                item { GroupTitle(group.locale.getDisplayLanguage(preferred).replaceFirstChar { it.uppercase() }) }
                voiceRows(group.voices, selected, onAction)
            }
        }
    }
}

private fun LazyListScope.voiceRows(voices: List<VoiceOption>, selected: String?, onAction: (ReaderAction) -> Unit) {
    items(voices, key = { it.id }) { voice ->
        VoiceRow(voiceLabel(voice), voice.id == selected) { onAction(ReaderAction.SetReadAloudVoice(voice.id)) }
    }
}

@Composable
private fun voiceLabel(voice: VoiceOption): String {
    val connection = stringResource(if (voice.isNetwork) R.string.read_aloud_network else R.string.read_aloud_offline)
    val place = voice.locale.getDisplayName(voice.locale).replaceFirstChar { it.uppercase() }
    return listOfNotNull(place, voice.variant, connection).joinToString(SEPARATOR)
}

@Composable
private fun Header(onCollapse: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = EdgePadding, end = EdgePadding / 2), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.read_aloud), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onCollapse) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.read_aloud_collapse))
        }
    }
}

/** How far through the document: the pages before, and the sentences of this one. */
@Composable
private fun Progress(state: ReaderUiState) {
    val spoken = state.spoken
    val page = spoken?.page ?: state.page
    val done = if (spoken != null) (spoken.index + 1f) / spoken.total else 0f
    val fraction = if (state.pages > 0) ((page - 1 + done) / state.pages).coerceIn(0f, 1f) else 0f
    if (spoken != null) {
        Text(
            text = stringResource(R.string.read_aloud_where, spoken.page, spoken.index + 1, spoken.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = EdgePadding),
        )
    }
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().padding(horizontal = EdgePadding, vertical = ProgressPadding),
    )
}

@Composable
private fun Transport(state: ReaderUiState, onAction: (ReaderAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = TransportPadding),
        horizontalArrangement = Arrangement.spacedBy(TransportSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onAction(ReaderAction.PreviousReadAloudPage) }) {
            Icon(Icons.Filled.FastRewind, contentDescription = stringResource(R.string.read_aloud_previous))
        }
        IconButton(onClick = { onAction(ReaderAction.PreviousReadAloudSentence) }) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.read_aloud_previous_sentence))
        }
        PlayPauseButton(state.readAloud is ReadAloudState.Reading, BigPlaySize, BigPlayIconPadding, onAction)
        IconButton(onClick = { onAction(ReaderAction.NextReadAloudSentence) }) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.read_aloud_next_sentence))
        }
        IconButton(onClick = { onAction(ReaderAction.NextReadAloudPage) }) {
            Icon(Icons.Filled.FastForward, contentDescription = stringResource(R.string.read_aloud_next))
        }
    }
}

/** The label follows the finger; the voice changes only when the finger lifts, so it does not stutter. */
@Composable
private fun Speed(rate: Float, onAction: (ReaderAction) -> Unit) {
    var dragged by remember(rate) { mutableFloatStateOf(rate) }
    Row(Modifier.fillMaxWidth().padding(horizontal = EdgePadding), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.read_aloud_speed), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.read_aloud_rate, rateLabel(dragged)), style = MaterialTheme.typography.labelLarge)
    }
    Slider(
        value = dragged.coerceIn(SpeechRates.MIN, SpeechRates.MAX),
        onValueChange = { dragged = it },
        onValueChangeFinished = { onAction(ReaderAction.SetReadAloudRate(dragged)) },
        valueRange = SpeechRates.MIN..SpeechRates.MAX,
        modifier = Modifier.fillMaxWidth().padding(horizontal = EdgePadding),
    )
}

@Composable
private fun SectionLabel(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = EdgePadding, end = EdgePadding, top = SectionTop, bottom = SectionBottom),
    )
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = EdgePadding, end = EdgePadding, top = GroupTop),
    )
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = RowHeight).padding(horizontal = EdgePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.read_aloud_voice_selected),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
