package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState
import io.github.martindavinci.lunaris.pdf.tts.SpeechRates
import io.github.martindavinci.lunaris.pdf.tts.SpokenSentence

private const val SEPARATOR = " · "
private val CardMargin = 12.dp
private val CardMinHeight = 72.dp
private val CardShape = RoundedCornerShape(24.dp)
private val CardElevation = 6.dp
private val PlaySize = 48.dp
private val PlayIconPadding = 12.dp
private val TextPadding = 12.dp
private val MessagePadding = 16.dp
private val SmallButton = 40.dp

/** Stays up while reading aloud, with or without the other bars. The chevron opens the rest of the controls. */
@Composable
internal fun ReadAloudBar(
    state: ReadAloudState,
    spoken: SpokenSentence?,
    rate: Float,
    onExpand: () -> Unit,
    onAction: (ReaderAction) -> Unit,
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = CardElevation,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(CardMargin),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = CardMinHeight).padding(start = CardMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is ReadAloudState.Reading -> Controls(state.page, playing = true, spoken, rate, onExpand, onAction)
                is ReadAloudState.Paused -> Controls(state.page, playing = false, spoken, rate, onExpand, onAction)
                is ReadAloudState.NoText -> Message(R.string.read_aloud_no_text)
                ReadAloudState.Unavailable -> Message(R.string.read_aloud_unavailable)
                ReadAloudState.Idle -> Unit
            }
            SmallIconButton(Icons.Filled.Close, R.string.read_aloud_stop) { onAction(ReaderAction.StopReadAloud) }
        }
    }
}

@Composable
private fun RowScope.Controls(
    page: Int,
    playing: Boolean,
    spoken: SpokenSentence?,
    rate: Float,
    onExpand: () -> Unit,
    onAction: (ReaderAction) -> Unit,
) {
    PlayPauseButton(playing, PlaySize, PlayIconPadding, onAction)
    Column(Modifier.weight(1f).padding(start = TextPadding)) {
        Text(
            text = stringResource(if (playing) R.string.read_aloud_page else R.string.read_aloud_paused, page),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val speed = stringResource(R.string.read_aloud_rate, rateLabel(rate))
        Text(
            text = if (spoken == null) speed else speed + SEPARATOR + stringResource(R.string.read_aloud_sentence, spoken.index + 1, spoken.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    SmallIconButton(Icons.Filled.SkipPrevious, R.string.read_aloud_previous_sentence) { onAction(ReaderAction.PreviousReadAloudSentence) }
    SmallIconButton(Icons.Filled.SkipNext, R.string.read_aloud_next_sentence) { onAction(ReaderAction.NextReadAloudSentence) }
    SmallIconButton(Icons.Filled.KeyboardArrowUp, R.string.read_aloud_expand, onExpand)
}

@Composable
private fun SmallIconButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(SmallButton)) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

/** The filled round button, in the bar and in the panel. */
@Composable
internal fun PlayPauseButton(playing: Boolean, size: androidx.compose.ui.unit.Dp, padding: androidx.compose.ui.unit.Dp, onAction: (ReaderAction) -> Unit) {
    Surface(
        onClick = { onAction(if (playing) ReaderAction.PauseReadAloud else ReaderAction.ResumeReadAloud) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(size),
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.read_aloud_pause else R.string.read_aloud_resume),
            modifier = Modifier.padding(padding),
        )
    }
}

/** "1.0", "2.5": one decimal always, so the label does not jump while the slider moves. */
internal fun rateLabel(rate: Float): String = SpeechRates.snap(rate).toString()

@Composable
private fun RowScope.Message(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = MessagePadding - CardMargin, top = MessagePadding, bottom = MessagePadding).weight(1f),
    )
}
