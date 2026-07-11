package tools.lunaris.texteditor.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tools.lunaris.texteditor.tts.ReaderState
import tools.lunaris.texteditor.tts.VoiceGroup

/** Two-step bottom controller: a mini-bar that expands into the full panel (with a scrim).
 *  The scrim / collapse chevron return to mini; only the header stop-square ends reading. */
@Composable
fun ReadingController(
    state: ReaderState,
    voiceGroups: List<VoiceGroup>,
    statusVoice: String,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onRate: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = false },
            )
            ReadingPanel(
                state = state,
                voiceGroups = voiceGroups,
                onPlayPause = onPlayPause,
                onPrev = onPrev,
                onNext = onNext,
                onCollapse = { expanded = false },
                onSelectVoice = onSelectVoice,
                onRate = onRate,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            ReadingMiniBar(
                state = state,
                statusVoice = statusVoice,
                onPlayPause = onPlayPause,
                onPrev = onPrev,
                onNext = onNext,
                onExpand = { expanded = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.4f
