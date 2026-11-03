package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.tts.ReadAloudState
import io.github.martindavinci.lunaris.pdf.tts.SpeechRates
import io.github.martindavinci.lunaris.pdf.tts.SpokenSentence

private val CardMargin = 12.dp
private val CardMinHeight = 72.dp
private val CardShape = RoundedCornerShape(24.dp)
private val CardElevation = 6.dp
private val PlaySize = 48.dp
private val PlayIconPadding = 12.dp
private val TextPadding = 12.dp
private val MessagePadding = 16.dp
private val SecondLineSpacing = 8.dp
private val RateShape = RoundedCornerShape(8.dp)
private val RatePaddingHorizontal = 6.dp
private val RatePaddingVertical = 1.dp

/** Stays up while reading aloud, with or without the other bars. */
@Composable
internal fun ReadAloudBar(
    state: ReadAloudState,
    spoken: SpokenSentence?,
    rate: Float,
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
                is ReadAloudState.Reading -> Controls(state.page, playing = true, spoken, rate, onAction)
                is ReadAloudState.Paused -> Controls(state.page, playing = false, spoken, rate, onAction)
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
    spoken: SpokenSentence?,
    rate: Float,
    onAction: (ReaderAction) -> Unit,
) {
    Surface(
        onClick = { onAction(if (playing) ReaderAction.PauseReadAloud else ReaderAction.ResumeReadAloud) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(PlaySize),
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.read_aloud_pause else R.string.read_aloud_resume),
            modifier = Modifier.padding(PlayIconPadding),
        )
    }
    Column(Modifier.weight(1f).padding(start = TextPadding)) {
        Text(
            text = stringResource(if (playing) R.string.read_aloud_page else R.string.read_aloud_paused, page),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SecondLineSpacing)) {
            Surface(
                onClick = { onAction(ReaderAction.NextReadAloudRate) },
                shape = RateShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = stringResource(R.string.read_aloud_rate, rateLabel(rate)),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = RatePaddingHorizontal, vertical = RatePaddingVertical),
                )
            }
            if (spoken != null) {
                Text(
                    text = stringResource(R.string.read_aloud_sentence, spoken.index + 1, spoken.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    IconButton(onClick = { onAction(ReaderAction.PreviousReadAloudPage) }) {
        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.read_aloud_previous))
    }
    IconButton(onClick = { onAction(ReaderAction.NextReadAloudPage) }) {
        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.read_aloud_next))
    }
}

/** "1.0", "1.25". */
private fun rateLabel(rate: Float): String = if (rate == SpeechRates.NORMAL) "1.0" else rate.toString()

@Composable
private fun RowScope.Message(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = MessagePadding - CardMargin, top = MessagePadding, bottom = MessagePadding).weight(1f),
    )
}
